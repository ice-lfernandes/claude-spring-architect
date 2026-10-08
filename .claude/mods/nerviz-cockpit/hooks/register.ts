// nerviz-cockpit — draws what ArchHook already decides. Nothing here decides anything.
//
//   band     AbovePrompt: the open skill phase, its class and the paths it may write,
//            from `java -jar .claude/hooks/ArchHook.jar guard status`
//   doctor   /nerviz-doctor: runs `ArchHook.jar doctor` and shows it in a pane, no turn spent
//   dialog   tool.call on Bash: when `guard` refused the command, holds the refusal behind two
//            buttons — let Claude adapt, or stop the turn. The refusal stands either way
//
// Why this is a mod (Form 9 of claude-code-architect-designer): axis 1 — the open phase and
// its write territory were invisible until `guard` refused a write, and no settings hook can
// draw. The closest rejected form was a `UserPromptSubmit` hook adding the phase as context:
// it reaches the model, not the person, and costs context on every prompt. Every rule stays
// in ArchHook.java (invariant 2) and every refusal stays a settings hook (invariant 6) — this
// mod does not load in `claude -p`, VS Code, a cloud session or under `--safe-mode`, and
// nothing breaks when it is absent. Allowed events, calls and programs: the `mods` block of
// .claude/schemas/extensions.json. Tested against Claude Code 2.1.293.
//
// Design: .claude/decisions/0131-mods-in-architect-designer.md. The spinner suffix, which showed
// the turn's cost in USD, was removed by .claude/decisions/0134-audit-tokens-only-no-usd-pricing.md.
//
// Every hook that can gate an event ends in `.catch(($, e, next) => next(e))`: a hook that
// throws then replays what the guards beneath already decided, or runs them once — never a
// call let through past them, never a refusal of this mod's own.
//
// The engine reads `on(...)` and `$.noun.method(...)` from source, so they are spelled
// literally, and helpers that take `$` are top-level functions.

import type { EngineInterface, Register } from 'claude-code'

const JAR = '.claude/hooks/ArchHook.jar'
const PANE = 'nerviz-doctor'
const LET_ADAPT = 'Let Claude adapt'
const STOP_TURN = 'Stop the turn'
const TERRITORY_SHOWN = 3

type Phase = { skills: string[]; cls: string | null; writeAllow: string[]; markers: string[] }

const NO_PHASE: Phase = { skills: [], cls: null, writeAllow: [], markers: [] }

let phase: Phase = NO_PHASE
let turnId: string | null = null
let doctor: string[] | null = null

export const register: Register = (on) => {
  on('session.start', async ($, e, next) => {
    const result = await next(e)
    try {
      await $.command.register({
        name: 'nerviz-doctor',
        description: 'ArchHook doctor in a pane, without spending a turn',
        immediate: true,
      })
    } catch {
      // The name is taken; /arch-doctor still reports the same lines.
    }
    await readPhase($)
    return result
  })

  // `guard prompt` opens or closes the phase in the settings hooks beneath this one.
  on('classic.UserPromptSubmit', async ($, e, next) => {
    const result = await next(e)
    await readPhase($)
    return result
  }).catch(($, e, next) => next(e))

  on('turn.start', async ($, e, next) => {
    turnId = e.turnId
    return next(e)
  })

  on('turn.complete', async ($, e, next) => {
    const result = await next(e)
    if (!e.agentId) turnId = null
    return result
  })

  // `guard call` joins a skill to the phase in the PreToolUse hook beneath this one.
  on('tool.call', { tool: 'Skill' }, async ($, e, next) => {
    const result = await next(e)
    await readPhase($)
    return result
  }).catch(($, e, next) => next(e))

  on('tool.call', { tool: 'Bash' }, async ($, e, next) => {
    const result = await next(e)
    if (typeof result.deny !== 'string' || !isGuardRefusal(result.deny)) return result
    if ((await $.session.surfaces()).length === 0) return result
    let answer = LET_ADAPT
    try {
      answer = await $.ui.ask(question(result.deny), { header: 'guard', options: [LET_ADAPT, STOP_TURN] })
    } catch {
      return result
    }
    if (answer === STOP_TURN && turnId !== null) {
      try {
        await $.turn.abort({ turnId })
      } catch {
        // The turn already ended.
      }
    }
    return result
  }).catch(($, e, next) => next(e))

  on('command.run', { command: 'nerviz-doctor' }, async ($, e, next) => {
    const root = await $.session.root()
    try {
      const run = await $.process.run(['java', '-jar', JAR, 'doctor'], { cwd: root, timeoutMs: 120000 })
      doctor = (run.stdout + run.stderr).split('\n').filter((line) => line.trim() !== '')
    } catch {
      doctor = ['ArchHook doctor did not run: no java on PATH, or ' + JAR + ' is missing.']
    }
    if ((await $.session.surfaces()).length > 0) {
      const opened = await $.ui.open({ id: PANE, title: 'nerviz doctor', focus: true, closeOnEscape: true })
      if (opened.isPlaced) return {}
    }
    return { text: doctor.join('\n') }
  })

  on('ui.render', { component: 'Pane' }, ($, e, next) => {
    if (e.requestId !== PANE || doctor === null) return next(e)
    const { Box, Text } = $.ui.resolve(e)
    return Box({
      flexDirection: 'column',
      paddingX: 1,
      children: doctor.map((line) => Text({ color: lineColor(line), wrap: 'truncate-end', children: line })),
    })
  })

  on('ui.render', { component: 'AbovePrompt' }, async ($, e, next) => {
    if (e.props.hasSurvey || phase.skills.length === 0) return next(e)
    const { Box, Text } = $.ui.resolve(e)
    const theirs = await next(e)
    const mine = Box({
      key: 'nerviz-phase',
      flexDirection: 'row',
      paddingX: 1,
      children: [
        Text({ color: 'cyan', bold: true, children: '▍ ' + (phase.cls ?? 'no class') }),
        Text({ children: '  ' + phase.skills.join(' + ') }),
        Text({ dimColor: true, wrap: 'truncate-end', children: '  writes ' + territory() }),
      ],
    })
    return theirs ? Box({ flexDirection: 'column', children: [mine, theirs] }) : mine
  })
}

// ---- What the guard says ---------------------------------------------------

async function readPhase($: EngineInterface) {
  try {
    const root = await $.session.root()
    const run = await $.process.run(['java', '-jar', JAR, 'guard', 'status'], {
      cwd: root,
      stdin: JSON.stringify({ session_id: await $.session.id() }),
      timeoutMs: 15000,
    })
    phase = run.exitCode === 0 ? parsePhase(run.stdout) : NO_PHASE
  } catch {
    phase = NO_PHASE
  }
  $.ui.invalidate('ui.render')
}

function parsePhase(stdout: string): Phase {
  try {
    const status = JSON.parse(stdout)
    return {
      skills: Array.isArray(status.phase) ? status.phase : [],
      cls: typeof status.class === 'string' ? status.class : null,
      writeAllow: Array.isArray(status.write_allow) ? status.write_allow : [],
      markers: Array.isArray(status.deny_markers) ? status.deny_markers : [],
    }
  } catch {
    return NO_PHASE
  }
}

function isGuardRefusal(deny: string): boolean {
  return phase.markers.some((marker) => deny.includes(marker))
}

function question(deny: string): string {
  const first = deny.split('\n').find((line) => line.trim() !== '') ?? deny
  return 'guard refused this command: ' + first.trim() + '\nThe refusal stands. What should Claude do next?'
}

// ---- What the band draws -----------------------------------------------------

function territory(): string {
  if (phase.writeAllow.length === 0) return 'nothing'
  const shown = phase.writeAllow.slice(0, TERRITORY_SHOWN).join(', ')
  const rest = phase.writeAllow.length - TERRITORY_SHOWN
  return rest > 0 ? shown + ' +' + rest : shown
}

function lineColor(line: string): string | undefined {
  if (line.includes('❌')) return 'red'
  if (line.includes('✅')) return 'green'
  if (line.includes('⚠')) return 'yellow'
  return undefined
}

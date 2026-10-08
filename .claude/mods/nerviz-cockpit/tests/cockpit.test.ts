// What nerviz-cockpit claims, against the engine's own `$` (claude plugin test). The guard
// itself is a stub here: `java -jar ArchHook.jar guard status` answers through `process.run`,
// and a refused Bash call is the stub beneath `tool.call`. What the jar prints is proven by
// .claude/.ci/GuardStatusTest.java; this file proves what the mod does with it.

import { expect, test } from 'claude-code/testing'

const OPEN = JSON.stringify({
  phase: ['claude-code-architect-designer'],
  class: 'meta',
  write_allow: ['.claude/**', 'CLAUDE.md', '.mcp.json', 'MCP-SETUP.md', 'docs/**', '.github/**'],
  deny_markers: ['guard: force push blocked', 'is outside its territory'],
})
const CLOSED = JSON.stringify({ phase: [], class: null, write_allow: [], deny_markers: ['is outside its territory'] })

const BAND = {
  plugin: 'nerviz-cockpit',
  component: 'AbovePrompt',
  props: {
    hasSurvey: false,
    isWorking: false,
    maxRows: 10,
    bodyColumns: 120,
    scroll: { offset: 0, bodyRows: 10 },
    view: {},
  },
} as const

const START = { cwd: '/repo', surface: 'terminal', isInteractive: true } as const

function engine(on, status: string, calls: string[][] = [], surfaces: string[] = ['terminal']) {
  on('session.root', () => ({ value: '/repo' }))
  on('session.id', () => ({ value: 'session-1' }))
  on('session.surfaces', () => ({ value: surfaces }))
  on('session.usage', () => ({ value: { context: { tokens: 0, window: 1, percent: 0 }, rateLimits: [] } }))
  on('command.register', () => ({ value: undefined }))
  on('session.start', ($, e) => ({ cwd: e.cwd }))
  on('process.run', ($, e) => {
    calls.push([...e.argv])
    return { value: { exitCode: 0, stdout: e.argv.includes('status') ? status : 'Schema ............ ✅ all pass', stderr: '' } }
  })
  on('ui.render', ($, e) => ({ type: 'Text', props: {}, children: [String(e.props?.suffix ?? '')] }))
}

// Beneath the mod: the Bash call refused with `deny`, and the dialog `$.ui.ask` raises as an
// AskUserQuestion call, answered "Let Claude adapt".
function answer(e, asked: string[], deny: string) {
  if (e.tool !== 'AskUserQuestion') return { deny }
  const question = String(e.questions[0].question)
  asked.push(question)
  return { result: { questions: e.questions, answers: { [question]: 'Let Claude adapt' } } }
}

test('the band shows the open phase, its class and its territory', async ($, on) => {
  const calls: string[][] = []
  engine(on, OPEN, calls)
  await $.session.start(START)
  expect(calls[0]).toEqual(['java', '-jar', '.claude/hooks/ArchHook.jar', 'guard', 'status'])
  const ui = await $.ui.mount({ ...BAND, surface: 'terminal' })
  expect((await ui.find({ type: 'Text', text: /meta/ }))?.text).toContain('meta')
  expect(await ui.find({ type: 'Text', text: /claude-code-architect-designer/ })).toBeDefined()
  expect((await ui.find({ type: 'Text', text: /writes/ }))?.text).toContain('+3')
  await ui.unmount()
})

test('no phase open, no band of its own', async ($, on) => {
  engine(on, CLOSED)
  await $.session.start(START)
  const ui = await $.ui.mount({ ...BAND, surface: 'terminal' })
  expect(await ui.find({ key: 'nerviz-phase' })).toBeUndefined()
  await ui.unmount()
})

test('the spinner names the open phase', async ($, on) => {
  engine(on, OPEN)
  await $.session.start(START)
  const ui = await $.ui.mount({
    plugin: 'nerviz-cockpit',
    surface: 'terminal',
    component: 'Spinner',
    props: { word: 'Sauteing', message: null, suffix: '…', mode: 'responding' },
  })
  expect((await ui.find({ type: 'Text', text: /claude-code-architect-designer/ }))?.text).toBe('… · claude-code-architect-designer')
  await ui.unmount()
})

test('a guard refusal is held behind the dialog and stays a refusal', async ($, on) => {
  engine(on, OPEN)
  const asked: string[] = []
  on('tool.call', ($, e) => answer(e, asked, '❌ `claude-code-architect-designer` is class `meta` — src/A.java is outside its territory.'))
  await $.session.start(START)
  const result = await $.tool.call({ tool: 'Bash', command: 'echo x > src/A.java' })
  expect(asked).toHaveLength(1)
  expect(result.deny).toContain('outside its territory')
})

test('a refusal the guard did not write asks nothing', async ($, on) => {
  engine(on, OPEN)
  const asked: string[] = []
  on('tool.call', ($, e) => answer(e, asked, 'The user does not want to run this command.'))
  await $.session.start(START)
  const result = await $.tool.call({ tool: 'Bash', command: 'rm -rf build' })
  expect(asked).toHaveLength(0)
  expect(result.deny).toContain('does not want')
})

test('/nerviz-doctor prints the report where nothing draws', async ($, on) => {
  engine(on, CLOSED, [], [])
  await $.session.start(START)
  const answer = await $.command.run({ command: 'nerviz-doctor', args: '' })
  expect(answer.text).toContain('Schema')
})

import { execFileSync } from 'node:child_process'
import { readFileSync, appendFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const run = (command, args) => execFileSync(command, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
const repository = process.env.GITHUB_REPOSITORY
const head = process.env.GITHUB_SHA
if (!repository || !/^[a-f0-9]{40}$/.test(head ?? '')) throw new Error('GitHub repository and commit required')
const pulls = JSON.parse(run('gh', ['api', `repos/${repository}/commits/${head}/pulls`]))
const promotion = pulls.find(pr => pr.merged_at && pr.base.ref === 'main' && pr.head.ref === 'develop' && pr.head.repo?.full_name === repository)
if (!promotion || !/^[a-f0-9]{40}$/.test(promotion.head.sha)) throw new Error('Production requires a merged develop to main PR')
const source = promotion.head.sha
run('git', ['merge-base', '--is-ancestor', source, head])
run('git', ['diff', '--exit-code', source, head, '--', 'oficina', 'frontend', 'deploy', 'scripts', 'Dockerfile', '.dockerignore', '.github'])
const runs = JSON.parse(run('gh', ['run', 'list', '--repo', repository, '--workflow', 'deploy.yml', '--branch', 'develop', '--commit', source,
  '--status', 'success', '--json', 'databaseId,headSha', '--limit', '20']))
const successful = runs.find(candidate => candidate.headSha === source)
if (!successful) throw new Error('No successful hml deployment for the promoted commit')
const directory = mkdtempSync(join(tmpdir(), 'oficina-promotion-'))
run('gh', ['run', 'download', String(successful.databaseId), '--repo', repository, '--name', `hml-release-${source}`, '--dir', directory])
const release = JSON.parse(readFileSync(join(directory, 'release.json'), 'utf8'))
if (release.sha !== source || release.environment !== 'hml' || !/^sha256:[a-f0-9]{64}$/.test(release.digest)) {
  throw new Error('Invalid hml release attestation')
}
if (release.repository !== process.env.ECR_REPOSITORY_URL) throw new Error('Promotion repository mismatch')
appendFileSync(process.env.GITHUB_ENV, `RELEASE_SHA=${source}\nIMAGE_DIGEST=${release.digest}\n`)

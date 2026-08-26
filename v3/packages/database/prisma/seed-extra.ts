import 'dotenv/config'
import { PrismaClient, IssueType, IssueStatus, Priority } from '@prisma/client'

if (process.env.DIRECT_URL) {
  process.env.DATABASE_URL = process.env.DIRECT_URL
}

const prisma = new PrismaClient()

const extraIssues: {
  ticketId: string
  title: string
  description: string
  type: IssueType
  priority: Priority
  status: IssueStatus
  requesterEmail: string
  assigneeEmail?: string
  cis?: string[]
  hoursAgo: number
}[] = [
  { ticketId: 'DEMO-101', title: 'Cannot access shared mailbox', description: 'Team mailbox request@work.local not syncing in Outlook.', type: 'INCIDENT', priority: 'HIGH', status: 'OPEN', requesterEmail: 'evan.brown@work.local', assigneeEmail: 'bob.martinez@work.local', hoursAgo: 1 },
  { ticketId: 'DEMO-102', title: 'Laptop keyboard replacement', description: 'Two keys are not registering on the finance laptop.', type: 'REQUEST', priority: 'MEDIUM', status: 'OPEN', requesterEmail: 'fay.green@work.local', assigneeEmail: 'alice.chen@work.local', cis: ['CI-004'], hoursAgo: 3 },
  { ticketId: 'DEMO-103', title: 'SFTP user account expiry', description: 'Automated SFTP account for payroll is expiring tonight.', type: 'INCIDENT', priority: 'MEDIUM', status: 'IN_PROGRESS', requesterEmail: 'evan.brown@work.local', assigneeEmail: 'bob.martinez@work.local', hoursAgo: 5 },
  { ticketId: 'DEMO-104', title: 'Wiki page editor broken', description: 'Confluence-like wiki editor shows blank screen for new pages.', type: 'BUG', priority: 'LOW', status: 'OPEN', requesterEmail: 'carol.smith@work.local', assigneeEmail: 'dave.jones@work.local', hoursAgo: 8 },
  { ticketId: 'DEMO-105', title: 'Request Adobe Creative Cloud license', description: 'Designer needs Photoshop and Illustrator for marketing assets.', type: 'REQUEST', priority: 'HIGH', status: 'ON_HOLD', requesterEmail: 'fay.green@work.local', assigneeEmail: 'alice.chen@work.local', hoursAgo: 12 },
  { ticketId: 'DEMO-106', title: 'Database backup verification failed', description: 'Nightly backup integrity check reported checksum mismatch.', type: 'INCIDENT', priority: 'HIGHEST', status: 'IN_PROGRESS', requesterEmail: 'alice.chen@work.local', assigneeEmail: 'alice.chen@work.local', cis: ['CI-001'], hoursAgo: 2 },
  { ticketId: 'DEMO-107', title: 'API response time regression', description: 'Core API p95 latency jumped from 120ms to 800ms.', type: 'BUG', priority: 'HIGH', status: 'OPEN', requesterEmail: 'dave.jones@work.local', assigneeEmail: 'carol.smith@work.local', cis: ['CI-003'], hoursAgo: 4 },
  { ticketId: 'DEMO-108', title: 'Monitor request for new hire', description: 'New engineer starts Monday and needs a 32-inch monitor.', type: 'REQUEST', priority: 'LOW', status: 'RESOLVED', requesterEmail: 'evan.brown@work.local', assigneeEmail: 'bob.martinez@work.local', hoursAgo: 24 },
  { ticketId: 'DEMO-109', title: 'Redis memory alert', description: 'Cache cluster memory usage above 85% for 30 minutes.', type: 'INCIDENT', priority: 'MEDIUM', status: 'OPEN', requesterEmail: 'carol.smith@work.local', assigneeEmail: 'dave.jones@work.local', cis: ['CI-006'], hoursAgo: 6 },
  { ticketId: 'DEMO-110', title: 'Google SSO redirect loop', description: 'Users get stuck in an infinite redirect after SSO login.', type: 'BUG', priority: 'HIGH', status: 'ON_HOLD', requesterEmail: 'fay.green@work.local', assigneeEmail: 'carol.smith@work.local', cis: ['CI-002'], hoursAgo: 10 },
  { ticketId: 'DEMO-111', title: 'Backup power supply test', description: 'Schedule UPS self-test for the server room this weekend.', type: 'REQUEST', priority: 'LOW', status: 'CLOSED', requesterEmail: 'bob.martinez@work.local', assigneeEmail: 'alice.chen@work.local', hoursAgo: 72 },
  { ticketId: 'DEMO-112', title: 'CI/CD pipeline failing after dependency update', description: 'New TypeScript version causes build errors in the web app.', type: 'BUG', priority: 'MEDIUM', status: 'IN_PROGRESS', requesterEmail: 'carol.smith@work.local', assigneeEmail: 'dave.jones@work.local', hoursAgo: 7 },
  { ticketId: 'DEMO-113', title: 'File server permissions reset', description: 'Finance folder permissions were reset during migration.', type: 'INCIDENT', priority: 'HIGH', status: 'RESOLVED', requesterEmail: 'evan.brown@work.local', assigneeEmail: 'bob.martinez@work.local', hoursAgo: 18 },
  { ticketId: 'DEMO-114', title: 'Headset for support team', description: 'Five noise-canceling headsets needed for the support desk.', type: 'REQUEST', priority: 'MEDIUM', status: 'OPEN', requesterEmail: 'fay.green@work.local', assigneeEmail: 'alice.chen@work.local', hoursAgo: 9 },
  { ticketId: 'DEMO-115', title: 'Network switch firmware update', description: 'Apply latest firmware patch to floor-3 access switch.', type: 'REQUEST', priority: 'HIGH', status: 'IN_PROGRESS', requesterEmail: 'bob.martinez@work.local', assigneeEmail: 'bob.martinez@work.local', cis: ['CI-005'], hoursAgo: 15 },
  { ticketId: 'DEMO-116', title: 'Mobile app crash on logout', description: 'App force-closes when the logout button is tapped twice.', type: 'BUG', priority: 'LOW', status: 'OPEN', requesterEmail: 'evan.brown@work.local', assigneeEmail: 'carol.smith@work.local', hoursAgo: 11 },
  { ticketId: 'DEMO-117', title: 'New SSL cert for load balancer', description: 'Renew and install the certificate on Azure load balancer.', type: 'REQUEST', priority: 'HIGH', status: 'ON_HOLD', requesterEmail: 'bob.martinez@work.local', assigneeEmail: 'bob.martinez@work.local', cis: ['CI-005'], hoursAgo: 20 },
  { ticketId: 'DEMO-118', title: 'Jira sync lag', description: 'Issues created in Jira take 15 minutes to appear in the portal.', type: 'BUG', priority: 'MEDIUM', status: 'CLOSED', requesterEmail: 'dave.jones@work.local', assigneeEmail: 'dave.jones@work.local', hoursAgo: 48 },
  { ticketId: 'DEMO-119', title: 'Printer out of toner', description: 'Floor-2 printer needs a new cyan toner cartridge.', type: 'INCIDENT', priority: 'LOW', status: 'OPEN', requesterEmail: 'fay.green@work.local', assigneeEmail: 'alice.chen@work.local', hoursAgo: 14 },
  { ticketId: 'DEMO-120', title: 'VPN token provisioning', description: 'Provision a new hardware token for the CFO travel.', type: 'REQUEST', priority: 'HIGH', status: 'IN_PROGRESS', requesterEmail: 'evan.brown@work.local', assigneeEmail: 'bob.martinez@work.local', hoursAgo: 16 },
  { ticketId: 'DEMO-121', title: 'Auth service memory leak', description: 'Auth pod memory grows until it is OOMKilled.', type: 'BUG', priority: 'HIGHEST', status: 'OPEN', requesterEmail: 'carol.smith@work.local', assigneeEmail: 'carol.smith@work.local', cis: ['CI-002'], hoursAgo: 1 },
  { ticketId: 'DEMO-122', title: 'Salesforce integration timeout', description: 'Opportunity sync job times out during peak hours.', type: 'INCIDENT', priority: 'MEDIUM', status: 'ON_HOLD', requesterEmail: 'evan.brown@work.local', assigneeEmail: 'dave.jones@work.local', hoursAgo: 21 },
  { ticketId: 'DEMO-123', title: 'New hire onboarding access', description: 'Create accounts for the new marketing hire starting Thursday.', type: 'REQUEST', priority: 'HIGH', status: 'RESOLVED', requesterEmail: 'fay.green@work.local', assigneeEmail: 'bob.martinez@work.local', hoursAgo: 30 },
  { ticketId: 'DEMO-124', title: 'Search index out of date', description: 'Knowledge base search returns articles deleted last month.', type: 'BUG', priority: 'LOW', status: 'OPEN', requesterEmail: 'dave.jones@work.local', assigneeEmail: 'dave.jones@work.local', hoursAgo: 13 },
  { ticketId: 'DEMO-125', title: 'Temperature alert in server room', description: 'HVAC sensor reports 28°C in the primary server room.', type: 'INCIDENT', priority: 'HIGH', status: 'IN_PROGRESS', requesterEmail: 'bob.martinez@work.local', assigneeEmail: 'alice.chen@work.local', hoursAgo: 3 },
  { ticketId: 'DEMO-126', title: 'MacBook dock not detected', description: 'Thunderbolt dock is not recognized after macOS update.', type: 'REQUEST', priority: 'LOW', status: 'OPEN', requesterEmail: 'fay.green@work.local', assigneeEmail: 'alice.chen@work.local', cis: ['CI-004'], hoursAgo: 17 },
  { ticketId: 'DEMO-127', title: 'Log aggregation gap', description: 'No application logs for the last 2 hours in the aggregator.', type: 'INCIDENT', priority: 'MEDIUM', status: 'OPEN', requesterEmail: 'carol.smith@work.local', assigneeEmail: 'dave.jones@work.local', hoursAgo: 19 },
  { ticketId: 'DEMO-128', title: 'Schedule access review', description: 'Quarterly access review for the finance department.', type: 'REQUEST', priority: 'MEDIUM', status: 'ON_HOLD', requesterEmail: 'evan.brown@work.local', assigneeEmail: 'bob.martinez@work.local', hoursAgo: 22 },
  { ticketId: 'DEMO-129', title: 'Database replica lag', description: 'Read replica is 15 minutes behind the primary.', type: 'BUG', priority: 'HIGH', status: 'IN_PROGRESS', requesterEmail: 'alice.chen@work.local', assigneeEmail: 'alice.chen@work.local', cis: ['CI-001'], hoursAgo: 5 },
  { ticketId: 'DEMO-130', title: 'Guest Wi-Fi password reset', description: 'Reset the weekly guest Wi-Fi passphrase and update signage.', type: 'REQUEST', priority: 'LOW', status: 'CLOSED', requesterEmail: 'fay.green@work.local', assigneeEmail: 'bob.martinez@work.local', hoursAgo: 96 },
]

const slaHours: Record<IssueType, Record<Priority, number>> = {
  INCIDENT: { LOWEST: 72, LOW: 24, MEDIUM: 8, HIGH: 4, HIGHEST: 1 },
  BUG: { LOWEST: 168, LOW: 72, MEDIUM: 24, HIGH: 8, HIGHEST: 4 },
  REQUEST: { LOWEST: 336, LOW: 168, MEDIUM: 72, HIGH: 24, HIGHEST: 8 },
}

async function main() {
  const users = await prisma.user.findMany({ select: { id: true, email: true } })
  const userByEmail = Object.fromEntries(users.map((u) => [u.email, u.id]))

  const cis = await prisma.configurationItem.findMany({ select: { id: true, ciId: true } })
  const ciById = Object.fromEntries(cis.map((c) => [c.ciId, c.id]))

  let created = 0
  for (const i of extraIssues) {
    const createdAt = new Date(Date.now() - i.hoursAgo * 60 * 60 * 1000)
    const hours = slaHours[i.type][i.priority]
    const slaTargetAt = new Date(createdAt.getTime() + hours * 60 * 60 * 1000)

    try {
      const issue = await prisma.issue.create({
        data: {
          ticketId: i.ticketId,
          title: i.title,
          description: i.description,
          type: i.type,
          status: i.status,
          priority: i.priority,
          requesterId: userByEmail[i.requesterEmail],
          assigneeId: i.assigneeEmail ? userByEmail[i.assigneeEmail] ?? null : null,
          slaTargetAt,
          createdAt,
          customFields: {},
        },
      })

      for (const ci of i.cis ?? []) {
        const ciId = ciById[ci]
        if (ciId) {
          await prisma.issueCI.create({ data: { issueId: issue.id, ciId } })
        }
      }
      created++
    } catch (err: any) {
      if (err.code !== 'P2002') throw err
    }
  }

  const counts = {
    users: await prisma.user.count(),
    issues: await prisma.issue.count(),
    cis: await prisma.configurationItem.count(),
  }

  console.log(`Created ${created} extra demo issues.`)
  console.log('Totals:', counts)
}

main()
  .catch((e) => {
    console.error(e)
    process.exit(1)
  })
  .finally(async () => {
    await prisma.$disconnect()
  })

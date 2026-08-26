import 'dotenv/config'
import { PrismaClient, IssueType, IssueStatus, Priority, Prisma } from '@prisma/client'
import { hash } from 'bcryptjs'

if (process.env.DIRECT_URL) {
  process.env.DATABASE_URL = process.env.DIRECT_URL
}

const prisma = new PrismaClient()

const PASSWORD = 'Password123!'

const permissions = [
  'admin:all',
  'user:read',
  'user:write',
  'issue:read',
  'issue:write',
  'issue:delete',
  'workflow:read',
  'workflow:write',
  'ci:read',
  'ci:write',
]

const roles = [
  { key: 'ADMIN', name: 'Administrator', perms: ['admin:all'] },
  { key: 'IT', name: 'IT Operator', perms: ['issue:read', 'issue:write', 'issue:delete', 'workflow:read', 'workflow:write', 'ci:read', 'ci:write', 'user:read'] },
  { key: 'MANAGER', name: 'Manager', perms: ['issue:read', 'issue:write', 'user:read'] },
  { key: 'EMPLOYEE', name: 'Employee', perms: ['issue:read', 'issue:write'] },
  { key: 'DEVELOPER', name: 'Developer', perms: ['issue:read', 'issue:write', 'ci:read', 'workflow:read', 'user:read'] },
]

const usersSeed = [
  { email: 'admin@work.local', name: 'Admin User', role: 'ADMIN' },
  { email: 'alice.chen@work.local', name: 'Alice Chen', role: 'IT' },
  { email: 'bob.martinez@work.local', name: 'Bob Martinez', role: 'IT' },
  { email: 'carol.smith@work.local', name: 'Carol Smith', role: 'DEVELOPER' },
  { email: 'dave.jones@work.local', name: 'Dave Jones', role: 'DEVELOPER' },
  { email: 'evan.brown@work.local', name: 'Evan Brown', role: 'EMPLOYEE' },
  { email: 'fay.green@work.local', name: 'Fay Green', role: 'EMPLOYEE' },
]

const cisSeed = [
  { ciId: 'CI-001', name: 'Prod-Postgres-DB', type: 'Database', category: 'Database', status: 'operational', ownerEmail: 'alice.chen@work.local' },
  { ciId: 'CI-002', name: 'Auth-Service-Pod', type: 'Service', category: 'Kubernetes', status: 'degraded', ownerEmail: 'carol.smith@work.local' },
  { ciId: 'CI-003', name: 'Core-API-Gateway', type: 'Service', category: 'API', status: 'operational', ownerEmail: 'dave.jones@work.local' },
  { ciId: 'CI-004', name: 'MacBook Pro M3 - CI-882', type: 'Hardware', category: 'Laptop', status: 'operational', ownerEmail: 'evan.brown@work.local' },
  { ciId: 'CI-005', name: 'Azure-Load-Balancer', type: 'Network', category: 'Network', status: 'operational', ownerEmail: 'bob.martinez@work.local' },
  { ciId: 'CI-006', name: 'Redis-Cache-Cluster', type: 'Cache', category: 'Cache', status: 'operational', ownerEmail: 'carol.smith@work.local' },
]

const relationships = [
  { from: 'CI-002', to: 'CI-001', type: 'depends_on' },
  { from: 'CI-003', to: 'CI-002', type: 'depends_on' },
  { from: 'CI-003', to: 'CI-006', type: 'depends_on' },
  { from: 'CI-005', to: 'CI-003', type: 'depends_on' },
  { from: 'CI-006', to: 'CI-001', type: 'depends_on' },
]

const issuesSeed = [
  { ticketId: 'INC-101', title: 'Database latency spike', description: 'Users reporting slow queries on the production Postgres cluster.', type: 'INCIDENT', priority: 'HIGHEST', status: 'IN_PROGRESS', requester: 'evan.brown@work.local', assignee: 'alice.chen@work.local', cis: ['CI-001'], created: new Date(Date.now() - 1000 * 60 * 60 * 2) },
  { ticketId: 'INC-102', title: 'Payment gateway timeout', description: 'Checkout page intermittently fails with 504 from payment provider.', type: 'INCIDENT', priority: 'HIGH', status: 'OPEN', requester: 'fay.green@work.local', assignee: null, cis: ['CI-003'], created: new Date(Date.now() - 1000 * 60 * 60 * 4) },
  { ticketId: 'BUG-201', title: 'Auth token refresh failure', description: 'Mobile clients fail to refresh JWT after 7 days.', type: 'BUG', priority: 'HIGH', status: 'OPEN', requester: 'evan.brown@work.local', assignee: 'carol.smith@work.local', cis: ['CI-002'], created: new Date(Date.now() - 1000 * 60 * 60 * 6) },
  { ticketId: 'BUG-202', title: 'Dashboard filter resets on refresh', description: 'Saved filters are lost when the user reloads the analytics page.', type: 'BUG', priority: 'MEDIUM', status: 'ON_HOLD', requester: 'fay.green@work.local', assignee: 'dave.jones@work.local', cis: ['CI-003'], created: new Date(Date.now() - 1000 * 60 * 60 * 12) },
  { ticketId: 'BUG-203', title: 'Email notifications not sending', description: 'Critical alert emails stuck in the outbound queue.', type: 'BUG', priority: 'LOW', status: 'RESOLVED', requester: 'fay.green@work.local', assignee: 'alice.chen@work.local', cis: [], created: new Date(Date.now() - 1000 * 60 * 60 * 24) },
  { ticketId: 'REQ-301', title: 'Request AWS Production Access', description: 'Engineer needs read-only access to production S3 and RDS.', type: 'REQUEST', priority: 'HIGH', status: 'OPEN', requester: 'evan.brown@work.local', assignee: 'bob.martinez@work.local', cis: [], created: new Date(Date.now() - 1000 * 60 * 60 * 3) },
  { ticketId: 'REQ-302', title: 'Request new monitor', description: 'Remote employee needs a 27-inch 4K monitor for home office.', type: 'REQUEST', priority: 'MEDIUM', status: 'IN_PROGRESS', requester: 'fay.green@work.local', assignee: 'alice.chen@work.local', cis: ['CI-004'], created: new Date(Date.now() - 1000 * 60 * 60 * 8) },
  { ticketId: 'REQ-303', title: 'VPN access for contractor', description: 'Grant temporary VPN for an external auditor next week.', type: 'REQUEST', priority: 'LOW', status: 'CLOSED', requester: 'evan.brown@work.local', assignee: 'bob.martinez@work.local', cis: [], created: new Date(Date.now() - 1000 * 60 * 60 * 48) },
  { ticketId: 'REQ-401', title: 'Refactor auth middleware', description: 'Reduce token verification complexity and add caching.', type: 'REQUEST', priority: 'MEDIUM', status: 'OPEN', requester: 'evan.brown@work.local', assignee: 'carol.smith@work.local', cis: ['CI-002'], created: new Date(Date.now() - 1000 * 60 * 60 * 10) },
  { ticketId: 'STORY-402', title: 'Implement Redis Caching', description: 'Cache frequently accessed catalog data to reduce DB load.', type: 'REQUEST', priority: 'HIGH', status: 'IN_PROGRESS', requester: 'fay.green@work.local', assignee: 'dave.jones@work.local', cis: ['CI-006'], created: new Date(Date.now() - 1000 * 60 * 60 * 14) },
  { ticketId: 'INC-103', title: 'CDN cache invalidation delay', description: 'New assets take up to 30 minutes to propagate.', type: 'INCIDENT', priority: 'MEDIUM', status: 'RESOLVED', requester: 'evan.brown@work.local', assignee: 'bob.martinez@work.local', cis: ['CI-005'], created: new Date(Date.now() - 1000 * 60 * 60 * 18) },
  { ticketId: 'BUG-204', title: 'Mobile login screen glitch', description: 'Login button unresponsive on iOS Safari after Face ID.', type: 'BUG', priority: 'LOW', status: 'OPEN', requester: 'fay.green@work.local', assignee: 'carol.smith@work.local', cis: [], created: new Date(Date.now() - 1000 * 60 * 60 * 20) },
  { ticketId: 'REQ-304', title: 'Audit log export', description: 'Export last quarter access logs for compliance review.', type: 'REQUEST', priority: 'LOW', status: 'ON_HOLD', requester: 'evan.brown@work.local', assignee: 'alice.chen@work.local', cis: [], created: new Date(Date.now() - 1000 * 60 * 60 * 36) },
  { ticketId: 'INC-104', title: 'SSL cert expiry warning', description: 'Wildcard cert expires in 9 days and needs renewal.', type: 'INCIDENT', priority: 'LOWEST', status: 'OPEN', requester: 'fay.green@work.local', assignee: null, cis: ['CI-005'], created: new Date(Date.now() - 1000 * 60 * 60 * 72) },
  { ticketId: 'BUG-205', title: 'Unit test flakiness in CI', description: 'Auth tests fail randomly due to timing issues.', type: 'BUG', priority: 'LOWEST', status: 'CLOSED', requester: 'carol.smith@work.local', assignee: 'dave.jones@work.local', cis: ['CI-003'], created: new Date(Date.now() - 1000 * 60 * 60 * 96) },
]

const slaHours: Record<IssueType, Record<Priority, number>> = {
  INCIDENT: { LOWEST: 72, LOW: 24, MEDIUM: 8, HIGH: 4, HIGHEST: 1 },
  BUG: { LOWEST: 168, LOW: 72, MEDIUM: 24, HIGH: 8, HIGHEST: 4 },
  REQUEST: { LOWEST: 336, LOW: 168, MEDIUM: 72, HIGH: 24, HIGHEST: 8 },
}

async function main() {
  const passwordHash = await hash(PASSWORD, 10)

  for (const key of permissions) {
    await prisma.permission.upsert({
      where: { key },
      update: {},
      create: { key, description: key },
    })
  }

  const permissionByKey = Object.fromEntries(
    (await prisma.permission.findMany()).map((p) => [p.key, p.id])
  )

  for (const role of roles) {
    const created = await prisma.role.upsert({
      where: { key: role.key },
      update: { name: role.name },
      create: { key: role.key, name: role.name, description: role.name },
    })
    for (const key of role.perms) {
      const permId = permissionByKey[key]
      if (permId) {
        await prisma.rolePermission.upsert({
          where: { roleId_permissionId: { roleId: created.id, permissionId: permId } },
          update: {},
          create: { roleId: created.id, permissionId: permId },
        })
      }
    }
  }

  const roleByKey = Object.fromEntries(
    (await prisma.role.findMany()).map((r) => [r.key, r.id])
  )

  const userByEmail: Record<string, string> = {}
  for (const u of usersSeed) {
    const roleId = roleByKey[u.role]
    const user = await prisma.user.upsert({
      where: { email: u.email },
      update: { name: u.name, status: 'ACTIVE' },
      create: { email: u.email, name: u.name, passwordHash, status: 'ACTIVE' },
    })
    await prisma.userRole.upsert({
      where: { userId_roleId: { userId: user.id, roleId } },
      update: {},
      create: { userId: user.id, roleId },
    })
    userByEmail[u.email] = user.id
  }

  const workflow = await prisma.workflow.upsert({
    where: { id: 'default-workflow' },
    update: {},
    create: { id: 'default-workflow', name: 'Default' },
  })

  const transitions: [IssueStatus, IssueStatus][] = [
    ['OPEN', 'IN_PROGRESS'],
    ['OPEN', 'ON_HOLD'],
    ['OPEN', 'RESOLVED'],
    ['OPEN', 'CLOSED'],
    ['OPEN', 'CANCELLED'],
    ['IN_PROGRESS', 'ON_HOLD'],
    ['IN_PROGRESS', 'RESOLVED'],
    ['IN_PROGRESS', 'CLOSED'],
    ['IN_PROGRESS', 'CANCELLED'],
    ['ON_HOLD', 'IN_PROGRESS'],
    ['ON_HOLD', 'RESOLVED'],
    ['ON_HOLD', 'CLOSED'],
    ['ON_HOLD', 'CANCELLED'],
    ['RESOLVED', 'CLOSED'],
    ['RESOLVED', 'IN_PROGRESS'],
    ['RESOLVED', 'OPEN'],
    ['CLOSED', 'OPEN'],
    ['CANCELLED', 'OPEN'],
  ]

  for (const [from, to] of transitions) {
    const existing = await prisma.workflowTransition.findFirst({
      where: { workflowId: workflow.id, fromStatus: from, toStatus: to },
    })
    if (!existing) {
      await prisma.workflowTransition.create({
        data: { workflowId: workflow.id, fromStatus: from, toStatus: to },
      })
    }
  }

  const ciById: Record<string, string> = {}
  for (const c of cisSeed) {
    const ci = await prisma.configurationItem.upsert({
      where: { ciId: c.ciId },
      update: {
        name: c.name,
        type: c.type,
        category: c.category,
        status: c.status,
        ownerId: userByEmail[c.ownerEmail] ?? null,
      },
      create: {
        ciId: c.ciId,
        name: c.name,
        type: c.type,
        category: c.category,
        status: c.status,
        ownerId: userByEmail[c.ownerEmail] ?? null,
      },
    })
    ciById[c.ciId] = ci.id
  }

  for (const r of relationships) {
    const fromId = ciById[r.from]
    const toId = ciById[r.to]
    if (fromId && toId) {
      await prisma.cIRelationship.upsert({
        where: { fromId_toId_type: { fromId, toId, type: r.type } },
        update: {},
        create: { fromId, toId, type: r.type },
      })
    }
  }

  for (const i of issuesSeed) {
    const hours = slaHours[i.type as IssueType][i.priority as Priority]
    const created = i.created
    const slaTargetAt = new Date(created.getTime() + hours * 60 * 60 * 1000)
    const issue = await prisma.issue.upsert({
      where: { ticketId: i.ticketId },
      update: {
        title: i.title,
        description: i.description,
        status: i.status as IssueStatus,
        priority: i.priority as Priority,
        requesterId: userByEmail[i.requester],
        assigneeId: i.assignee ? userByEmail[i.assignee] : null,
        slaTargetAt,
        createdAt: created,
      },
      create: {
        ticketId: i.ticketId,
        title: i.title,
        description: i.description,
        type: i.type as IssueType,
        status: i.status as IssueStatus,
        priority: i.priority as Priority,
        requesterId: userByEmail[i.requester],
        assigneeId: i.assignee ? userByEmail[i.assignee] : null,
        slaTargetAt,
        createdAt: created,
        customFields: {},
      },
    })

    for (const c of i.cis) {
      const ciId = ciById[c]
      if (ciId) {
        await prisma.issueCI.upsert({
          where: { issueId_ciId: { issueId: issue.id, ciId } },
          update: {},
          create: { issueId: issue.id, ciId },
        })
      }
    }
  }

  console.log('Seed complete.')
  console.log('- users:', await prisma.user.count())
  console.log('- issues:', await prisma.issue.count())
  console.log('- cis:', await prisma.configurationItem.count())
  console.log('- workflow transitions:', await prisma.workflowTransition.count())
}

main()
  .catch((e) => {
    console.error(e)
    process.exit(1)
  })
  .finally(async () => {
    await prisma.$disconnect()
  })

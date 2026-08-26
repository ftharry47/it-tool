const { PrismaClient } = require('@prisma/client')
const fs = require('fs')
const path = require('path')

const prisma = new PrismaClient()

const dbPath = path.resolve(__dirname, '..', '..', '..', 'db.json')

const statusMap = {
  'Submitted': 'SUBMITTED',
  'In Progress': 'IN_PROGRESS',
  'On Hold': 'ON_HOLD',
  'Resolved': 'RESOLVED',
  'Closed': 'CLOSED',
}

const priorityMap = {
  'Pending': 'PENDING',
  'Low': 'LOW',
  'Medium': 'MEDIUM',
  'High': 'HIGH',
  'Critical': 'CRITICAL',
}

async function main() {
  if (!fs.existsSync(dbPath)) {
    console.error('db.json not found at', dbPath)
    process.exit(1)
  }

  const data = JSON.parse(fs.readFileSync(dbPath, 'utf8'))
  const tickets = Array.isArray(data.tickets) ? data.tickets : []

  const usersByEmail = new Map()

  for (const t of tickets) {
    const email = t['Email Address']?.trim() || `unknown-${Math.random().toString(36).slice(2)}@devit.local`
    const name = t['Name']?.trim() || 'Unknown User'
    if (!usersByEmail.has(email.toLowerCase())) {
      usersByEmail.set(email.toLowerCase(), { email, name })
    }
  }

  for (const user of usersByEmail.values()) {
    await prisma.user.upsert({
      where: { email: user.email },
      update: {},
      create: { email: user.email, name: user.name, role: 'EMPLOYEE' },
    })
  }

  const userRecords = await prisma.user.findMany({ where: { email: { in: Array.from(usersByEmail.values()).map((u) => u.email) } } })
  const userByEmail = new Map(userRecords.map((u) => [u.email.toLowerCase(), u.id]))

  for (const t of tickets) {
    const email = (t['Email Address']?.trim() || '').toLowerCase()
    const requesterId = userByEmail.get(email)
    if (!requesterId) continue

    const status = statusMap[t['Status']] || 'SUBMITTED'
    const priority = priorityMap[t['Priority']] || 'PENDING'
    const impact = t['Critical Flag'] === 'true' ? 'HIGH' : 'MEDIUM'
    const title = `${t['Issue Type'] || 'Ticket'}: ${(t['Short Description'] || '').slice(0, 80)}`

    const createdAt = t['Created Date'] ? new Date(t['Created Date']) : undefined

    try {
      await prisma.issue.upsert({
        where: { ticketId: t['Ticket ID'] },
        update: {},
        create: {
          ticketId: t['Ticket ID'],
          type: 'INCIDENT',
          title,
          description: t['Short Description'] || title,
          impact,
          category: t['Issue Type'] || 'Other',
          status,
          priority,
          requesterId,
          createdAt,
        },
      })
    } catch (err) {
      console.error('Failed to import', t['Ticket ID'], err.message)
    }
  }

  console.log(`Imported ${tickets.length} tickets`)
  await prisma.$disconnect()
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})

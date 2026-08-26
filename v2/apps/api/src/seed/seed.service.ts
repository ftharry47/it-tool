import { Injectable, OnModuleInit, Logger } from '@nestjs/common'
import { ConfigService } from '@nestjs/config'
import { PrismaService } from '../prisma/prisma.service'

const defaultCatalog = [
  { key: 'laptop', name: 'Request a Laptop', description: 'New or replacement laptop for employee.', icon: 'Laptop', category: 'Hardware', issueType: 'REQUEST' },
  { key: 'software-license', name: 'Software License', description: 'Request a software license or SaaS access.', icon: 'Box', category: 'Software', issueType: 'REQUEST' },
  { key: 'access-request', name: 'Access Request', description: 'Request access to a system or folder.', icon: 'Lock', category: 'Access', issueType: 'REQUEST' },
  { key: 'network-outage', name: 'Network Outage', description: 'Report a network connectivity issue.', icon: 'WifiOff', category: 'Network', issueType: 'INCIDENT' },
  { key: 'email', name: 'Email Issue', description: 'Problems with email, calendar, or mailing lists.', icon: 'Mail', category: 'Software', issueType: 'INCIDENT' },
  { key: 'printer', name: 'Printer Problem', description: 'Printer is offline, jammed, or not responding.', icon: 'Printer', category: 'Hardware', issueType: 'INCIDENT' },
  { key: 'password-reset', name: 'Password Reset', description: 'Reset a forgotten or expired password.', icon: 'Key', category: 'Access', issueType: 'REQUEST' },
  { key: 'mobile-device', name: 'Mobile Device', description: 'Request or report a mobile device issue.', icon: 'Smartphone', category: 'Hardware', issueType: 'REQUEST' },
  { key: 'knowledge-base', name: 'Knowledge Base', description: 'Browse knowledge articles and self-service help.', icon: 'BookOpen', category: 'Knowledge', issueType: 'REQUEST' },
  { key: 'service-desk', name: 'Service Desk', description: 'General IT service desk request.', icon: 'Headphones', category: 'Support', issueType: 'REQUEST' },
]

const defaultUsers = [
  { email: 'admin@devit.local', name: 'Admin User', role: 'ADMIN' },
  { email: 'manager@devit.local', name: 'Manager User', role: 'MANAGER' },
  { email: 'it@devit.local', name: 'IT Agent', role: 'IT' },
  { email: 'employee1@devit.local', name: 'Employee One', role: 'EMPLOYEE' },
  { email: 'employee2@devit.local', name: 'Employee Two', role: 'EMPLOYEE' },
]

const defaultArticles = [
  { title: 'How to reset your password', content: 'Go to the password reset portal and follow the steps.', category: 'Access', tags: '[]' },
  { title: 'VPN connection troubleshooting', content: 'Check your network, restart the VPN client, and verify credentials.', category: 'Network', tags: '[]' },
  { title: 'Request a laptop', content: 'Submit a service catalog request and include your department.', category: 'Hardware', tags: '[]' },
]

const incidentForm = {
  name: 'Incident Form',
  issueType: 'INCIDENT',
  isActive: true,
  fields: [
    { name: 'Asset Tag', key: 'asset_tag', type: 'TEXT', required: false, options: null, order: 0 },
    { name: 'MAC Address', key: 'mac_address', type: 'TEXT', required: false, options: null, order: 1 },
  ],
}

const requestForm = {
  name: 'Request Form',
  issueType: 'REQUEST',
  isActive: true,
  fields: [
    { name: 'Department', key: 'department', type: 'TEXT', required: true, options: null, order: 0 },
    { name: 'Requested Completion Date', key: 'due_date', type: 'DATE', required: false, options: null, order: 1 },
  ],
}

@Injectable()
export class SeedService implements OnModuleInit {
  private readonly logger = new Logger(SeedService.name)

  constructor(private prisma: PrismaService, private config: ConfigService) {}

  async onModuleInit() {
    if (this.config.get('SEED') === 'false') return
    const existing = await this.prisma.user.count()
    if (existing > 0) {
      this.logger.log('Database already seeded.')
      return
    }

    const manager = await this.prisma.user.create({ data: { ...defaultUsers[1] } })
    const admin = await this.prisma.user.create({ data: { ...defaultUsers[0], managerId: manager.id } })
    const it = await this.prisma.user.create({ data: { ...defaultUsers[2], managerId: manager.id } })
    const employee1 = await this.prisma.user.create({ data: { ...defaultUsers[3], managerId: manager.id } })
    const employee2 = await this.prisma.user.create({ data: { ...defaultUsers[4], managerId: manager.id } })

    for (const item of defaultCatalog) {
      await this.prisma.catalogItem.create({ data: item })
    }

    for (const article of defaultArticles) {
      await this.prisma.knowledgeArticle.create({ data: article })
    }

    // Seed form definitions with fields
    for (const def of [incidentForm, requestForm]) {
      const { fields, ...rest } = def
      await this.prisma.formDefinition.create({
        data: {
          ...rest,
          fields: { create: fields as any },
        },
      })
    }

    // Seed a sample issue with custom fields
    await this.prisma.issue.create({
      data: {
        type: 'INCIDENT',
        title: 'Sample Network Outage',
        description: 'Cannot connect to the network in the east wing.',
        impact: 'HIGH',
        category: 'Network',
        status: 'SUBMITTED',
        priority: 'HIGH',
        requesterId: employee1.id,
        customFields: JSON.stringify({ asset_tag: 'AST-123', mac_address: '00:11:22:33:44:55' }),
      },
    })

    this.logger.log('Database seeded.')
  }
}

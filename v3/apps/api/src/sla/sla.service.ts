import { Injectable, OnModuleDestroy, OnModuleInit } from '@nestjs/common'
import { IssueStatus, Priority, IssueType } from '@work/database'
import { PrismaService } from '../prisma/prisma.service'

const DEFAULT_SLA_HOURS: { issueType: IssueType; priority: Priority; hours: number }[] = [
  { issueType: 'INCIDENT', priority: 'HIGHEST', hours: 4 },
  { issueType: 'INCIDENT', priority: 'HIGH', hours: 8 },
  { issueType: 'INCIDENT', priority: 'MEDIUM', hours: 24 },
  { issueType: 'INCIDENT', priority: 'LOW', hours: 72 },
  { issueType: 'REQUEST', priority: 'HIGH', hours: 24 },
  { issueType: 'REQUEST', priority: 'MEDIUM', hours: 48 },
  { issueType: 'REQUEST', priority: 'LOW', hours: 72 },
  { issueType: 'BUG', priority: 'HIGHEST', hours: 8 },
  { issueType: 'BUG', priority: 'HIGH', hours: 12 },
  { issueType: 'BUG', priority: 'MEDIUM', hours: 48 },
  { issueType: 'BUG', priority: 'LOW', hours: 72 },
]

@Injectable()
export class SlaService implements OnModuleInit, OnModuleDestroy {
  private fallbackInterval?: NodeJS.Timeout

  constructor(private readonly prisma: PrismaService) {}

  async onModuleInit() {
    await this.seedDefinitions()
    // Redis/BullMQ worker is optional; use in-process fallback for local dev
    this.fallbackInterval = setInterval(() => {
      this.checkBreaches().catch((e) => console.error('[SLA fallback] error', e))
    }, 60000)
  }

  onModuleDestroy() {
    if (this.fallbackInterval) clearInterval(this.fallbackInterval)
  }

  private async seedDefinitions() {
    for (const rule of DEFAULT_SLA_HOURS) {
      const existing = await this.prisma.slaDefinition.findFirst({
        where: {
          issueType: rule.issueType,
          priority: rule.priority,
        },
      })

      if (!existing) {
        await this.prisma.slaDefinition.create({
          data: {
            issueType: rule.issueType,
            priority: rule.priority,
            hours: rule.hours,
          },
        })
      }
    }
  }

  async checkBreaches() {
    const now = new Date()
    const terminal: IssueStatus[] = ['CLOSED', 'CANCELLED', 'RESOLVED']

    const issues = await this.prisma.issue.findMany({
      where: {
        slaBreached: false,
        slaTargetAt: { lt: now },
        status: { notIn: terminal },
      },
      select: { id: true },
    })

    for (const issue of issues) {
      await this.prisma.issue.update({
        where: { id: issue.id },
        data: { slaBreached: true },
      })
    }
  }

  async computeTarget(issue: { id: string; type: string; priority: string; createdAt: Date }) {
    const definition = await this.prisma.slaDefinition.findFirst({
      where: {
        issueType: issue.type as any,
        priority: issue.priority as any,
      },
    })

    if (!definition) return null

    const target = new Date(issue.createdAt.getTime() + definition.hours * 60 * 60 * 1000)
    await this.prisma.issue.update({
      where: { id: issue.id },
      data: { slaTargetAt: target },
    })

    return target
  }
}

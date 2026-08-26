import { Injectable, OnModuleInit } from '@nestjs/common'
import { Issue, IssueStatus, IssueType } from '@work/database'
import { PrismaService } from '../prisma/prisma.service'

export interface TransitionRule {
  from: IssueStatus
  to: IssueStatus
  issueType?: IssueType
  requiredPermission?: string
}

const DEFAULT_TRANSITIONS: TransitionRule[] = [
  { from: 'OPEN', to: 'IN_PROGRESS' },
  { from: 'OPEN', to: 'ON_HOLD' },
  { from: 'OPEN', to: 'CLOSED', requiredPermission: 'issue:delete' },
  { from: 'OPEN', to: 'CANCELLED', requiredPermission: 'issue:delete' },
  { from: 'IN_PROGRESS', to: 'ON_HOLD' },
  { from: 'IN_PROGRESS', to: 'RESOLVED' },
  { from: 'IN_PROGRESS', to: 'CLOSED', requiredPermission: 'issue:delete' },
  { from: 'IN_PROGRESS', to: 'CANCELLED', requiredPermission: 'issue:delete' },
  { from: 'ON_HOLD', to: 'IN_PROGRESS' },
  { from: 'ON_HOLD', to: 'CLOSED', requiredPermission: 'issue:delete' },
  { from: 'ON_HOLD', to: 'CANCELLED', requiredPermission: 'issue:delete' },
  { from: 'RESOLVED', to: 'CLOSED' },
  { from: 'RESOLVED', to: 'IN_PROGRESS' },
  { from: 'RESOLVED', to: 'OPEN', requiredPermission: 'issue:write' },
  { from: 'CLOSED', to: 'OPEN', requiredPermission: 'issue:write' },
  { from: 'CANCELLED', to: 'OPEN', requiredPermission: 'issue:write' },
]

export interface UserContext {
  userId: string
  roles: string[]
  permissions: string[]
}

@Injectable()
export class WorkflowsService implements OnModuleInit {
  constructor(private readonly prisma: PrismaService) {}

  async onModuleInit() {
    await this.seed()
  }

  async seed() {
    let workflow = await this.prisma.workflow.findFirst({
      where: { name: 'Default' },
    })

    if (!workflow) {
      workflow = await this.prisma.workflow.create({
        data: {
          name: 'Default',
          issueType: null,
        },
      })
    }

    for (const rule of DEFAULT_TRANSITIONS) {
      const existing = await this.prisma.workflowTransition.findFirst({
        where: {
          workflowId: workflow.id,
          fromStatus: rule.from,
          toStatus: rule.to,
        },
      })

      if (!existing) {
        await this.prisma.workflowTransition.create({
          data: {
            workflowId: workflow.id,
            fromStatus: rule.from,
            toStatus: rule.to,
            requiredPermissionKey: rule.requiredPermission,
          },
        })
      }
    }
  }

  async canTransition(issue: Issue, toStatus: IssueStatus, user: UserContext): Promise<boolean> {
    if (issue.status === toStatus) return true

    const workflow = await this.prisma.workflow.findFirst({
      where: { name: 'Default' },
      include: { transitions: true },
    })

    if (!workflow) return false

    const transition = workflow.transitions.find(
      (t) => t.fromStatus === issue.status && t.toStatus === toStatus,
    )

    if (!transition) return false

    if (user.roles.includes('ADMIN')) return true

    if (transition.requiredPermissionKey) {
      return user.permissions.includes(transition.requiredPermissionKey)
    }

    return user.permissions.includes('issue:write')
  }
}

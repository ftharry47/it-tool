import { ForbiddenException, Injectable, NotFoundException } from '@nestjs/common'
import { IsEnum, IsIn, IsInt, IsJSON, IsObject, IsOptional, IsString, Min, ValidateIf } from 'class-validator'
import { Issue, IssueStatus, IssueType, Priority, Prisma } from '@work/database'
import { PrismaService } from '../prisma/prisma.service'
import { SlaService } from '../sla/sla.service'
import { UserContext } from '../workflows/workflows.service'
import { WorkflowsService } from '../workflows/workflows.service'

export class CreateIssueDto {
  @IsEnum(IssueType)
  type!: IssueType

  @IsString()
  title!: string

  @IsString()
  @IsOptional()
  description?: string

  @IsEnum(Priority)
  @IsOptional()
  priority?: Priority

  @IsString()
  @IsOptional()
  category?: string

  @IsObject()
  @IsOptional()
  customFields?: Record<string, any>

  @IsString()
  @IsOptional()
  epicId?: string

  @IsString()
  @IsOptional()
  sprintId?: string

  @IsInt()
  @Min(0)
  @IsOptional()
  points?: number

  @IsString()
  @IsOptional()
  assigneeId?: string
}

export class UpdateIssueDto {
  @IsString()
  @IsOptional()
  title?: string

  @IsString()
  @IsOptional()
  description?: string

  @IsEnum(Priority)
  @IsOptional()
  priority?: Priority

  @IsObject()
  @IsOptional()
  customFields?: Record<string, any>

  @IsString()
  @IsOptional()
  epicId?: string

  @IsString()
  @IsOptional()
  sprintId?: string

  @IsInt()
  @Min(0)
  @IsOptional()
  points?: number

  @IsString()
  @IsOptional()
  assigneeId?: string
}

export class TransitionIssueDto {
  @IsEnum(IssueStatus)
  status!: IssueStatus
}

export class QueryIssuesDto {
  @IsEnum(IssueType)
  @IsOptional()
  type?: IssueType

  @IsEnum(IssueStatus)
  @IsOptional()
  status?: IssueStatus

  @IsEnum(Priority)
  @IsOptional()
  priority?: Priority

  @IsString()
  @IsOptional()
  requesterId?: string

  @IsString()
  @IsOptional()
  assigneeId?: string

  @IsString()
  @IsOptional()
  search?: string

  @IsInt()
  @Min(1)
  @IsOptional()
  take?: number

  @IsInt()
  @Min(0)
  @IsOptional()
  skip?: number
}

export class AddCommentDto {
  @IsString()
  content!: string
}

export class LinkCiDto {
  @IsString()
  ciId!: string
}

@Injectable()
export class IssuesService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly workflows: WorkflowsService,
    private readonly sla: SlaService,
  ) {}

  async create(dto: CreateIssueDto, requesterId: string) {
    const requester = await this.prisma.user.findUnique({
      where: { id: requesterId },
      include: { managedBy: true },
    })

    const data: Prisma.IssueCreateInput = {
      type: dto.type,
      title: dto.title,
      description: dto.description ?? null,
      priority: dto.priority ?? 'MEDIUM',
      customFields: (dto.customFields ?? {}) as any,
      requester: { connect: { id: requesterId } },
      boardColumn: 'todo',
      boardOrder: 0,
    }

    if (dto.assigneeId) {
      data.assignee = { connect: { id: dto.assigneeId } }
    }

    if (dto.epicId) {
      data.epic = { connect: { id: dto.epicId } }
    }

    if (dto.sprintId) {
      data.sprint = { connect: { id: dto.sprintId } }
    }

    if (dto.points) {
      data.points = dto.points
    }

    const issue = await this.prisma.issue.create({
      data,
      include: this.issueInclude(),
    })

    if (dto.type === 'REQUEST' && requester?.managerId) {
      await this.prisma.approval.create({
        data: {
          issueId: issue.id,
          managerId: requester.managerId,
          status: 'pending',
        },
      })
    }

    await this.sla.computeTarget(issue)

    return this.findOne(issue.id)
  }

  async findAll(query: QueryIssuesDto, user: UserContext) {
    const where: Prisma.IssueWhereInput = {}

    if (query.type) where.type = query.type
    if (query.status) where.status = query.status
    if (query.priority) where.priority = query.priority
    if (query.assigneeId) where.assigneeId = query.assigneeId

    const filters: Prisma.IssueWhereInput[] = []

    if (query.requesterId) filters.push({ requesterId: query.requesterId })

    if (query.search) {
      const q = query.search
      filters.push({
        OR: [
          { title: { contains: q, mode: 'insensitive' } },
          { description: { contains: q, mode: 'insensitive' } },
          { ticketId: { contains: q, mode: 'insensitive' } },
        ],
      })
    }

    const isAdmin = user.roles.includes('ADMIN') || user.permissions.includes('admin:all')
    if (!isAdmin) {
      filters.push({
        OR: [{ requesterId: user.userId }, { assigneeId: user.userId }],
      })
    }

    if (filters.length) where.AND = filters

    return this.prisma.issue.findMany({
      where,
      include: {
        requester: { select: { id: true, name: true, email: true } },
        assignee: { select: { id: true, name: true, email: true } },
        epic: { select: { id: true, key: true, title: true } },
        sprint: { select: { id: true, key: true, name: true } },
        cis: { include: { ci: { select: { id: true, ciId: true, name: true, type: true } } } },
        _count: { select: { comments: true } },
      },
      take: query.take ?? 50,
      skip: query.skip ?? 0,
      orderBy: { createdAt: 'desc' },
    })
  }

  async findOne(id: string) {
    const issue = await this.prisma.issue.findUnique({
      where: { id },
      include: this.issueInclude(),
    })
    if (!issue) throw new NotFoundException('Issue not found')
    return issue
  }

  async update(id: string, dto: UpdateIssueDto, user: UserContext) {
    const existing = await this.findOne(id)

    if (user.roles.includes('ADMIN') || existing.requesterId === user.userId) {
      // allowed
    } else if (user.permissions.includes('issue:write')) {
      // allowed for agents
    } else {
      throw new ForbiddenException('You cannot update this issue')
    }

    const data: Prisma.IssueUpdateInput = {
      title: dto.title,
      description: dto.description,
      priority: dto.priority,
      customFields: dto.customFields ? (dto.customFields as any) : undefined,
      points: dto.points,
    }

    if (dto.assigneeId) data.assignee = { connect: { id: dto.assigneeId } }
    if (dto.epicId) data.epic = { connect: { id: dto.epicId } }
    if (dto.sprintId) data.sprint = { connect: { id: dto.sprintId } }

    const updated = await this.prisma.issue.update({
      where: { id },
      data,
      include: this.issueInclude(),
    })

    if (dto.priority && dto.priority !== existing.priority) {
      await this.sla.computeTarget(updated)
    }

    return this.findOne(updated.id)
  }

  async transition(id: string, dto: TransitionIssueDto, user: UserContext) {
    const issue = await this.prisma.issue.findUnique({ where: { id } })
    if (!issue) throw new NotFoundException('Issue not found')

    const allowed = await this.workflows.canTransition(issue, dto.status, user)
    if (!allowed) {
      throw new ForbiddenException(`Cannot transition from ${issue.status} to ${dto.status}`)
    }

    const updated = await this.prisma.issue.update({
      where: { id },
      data: { status: dto.status },
    })

    await this.prisma.issueHistory.create({
      data: {
        issueId: id,
        field: 'status',
        oldValue: issue.status,
        newValue: dto.status,
        changedById: user.userId,
      },
    })

    return this.findOne(updated.id)
  }

  async remove(id: string, user: UserContext) {
    const existing = await this.findOne(id)
    if (!user.roles.includes('ADMIN') && !user.permissions.includes('issue:delete')) {
      throw new ForbiddenException('You cannot delete this issue')
    }

    await this.prisma.issue.delete({ where: { id } })
    return { id, deleted: true }
  }

  async addComment(id: string, userId: string, dto: AddCommentDto) {
    const issue = await this.findOne(id)
    const comment = await this.prisma.comment.create({
      data: {
        issueId: id,
        authorId: userId,
        content: dto.content,
      },
      include: {
        author: { select: { id: true, name: true, email: true } },
      },
    })
    return comment
  }

  async linkCi(id: string, dto: LinkCiDto) {
    await this.findOne(id)
    await this.prisma.issueCI.upsert({
      where: { issueId_ciId: { issueId: id, ciId: dto.ciId } },
      update: {},
      create: {
        issueId: id,
        ciId: dto.ciId,
      },
    })
    return this.findOne(id)
  }

  private issueInclude(): Prisma.IssueInclude {
    return {
      requester: { select: { id: true, name: true, email: true } },
      assignee: { select: { id: true, name: true, email: true } },
      epic: { select: { id: true, key: true, title: true } },
      sprint: { select: { id: true, key: true, name: true } },
      comments: {
        orderBy: { createdAt: 'desc' },
        include: {
          author: { select: { id: true, name: true, email: true } },
        },
      },
      cis: {
        include: {
          ci: { select: { id: true, ciId: true, name: true, type: true } },
        },
      },
      approvals: {
        include: {
          manager: { select: { id: true, name: true, email: true } },
        },
      },
      _count: { select: { comments: true } },
    }
  }
}

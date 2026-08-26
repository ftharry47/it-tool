import { Injectable, BadRequestException, NotFoundException } from '@nestjs/common'
import { PrismaService } from '../prisma/prisma.service'
import { CreateIssueDto } from './dto/create-issue.dto'

@Injectable()
export class IssuesService {
  constructor(private prisma: PrismaService) {}

  async findAll(requesterId?: string, assigneeId?: string) {
    return this.prisma.issue.findMany({
      where: { requesterId, assigneeId },
      orderBy: { createdAt: 'desc' },
      include: { requester: { select: { id: true, name: true, email: true } }, comments: true },
    })
  }

  async findOne(id: string) {
    const issue = await this.prisma.issue.findUnique({
      where: { id },
      include: {
        requester: { select: { id: true, name: true, email: true } },
        assignee: { select: { id: true, name: true, email: true } },
        comments: { include: { author: { select: { id: true, name: true } } }, orderBy: { createdAt: 'asc' } },
        approval: { include: { manager: { select: { id: true, name: true } } } },
      },
    })
    if (!issue) throw new NotFoundException('Issue not found')
    return issue
  }

  async create(dto: CreateIssueDto, requesterId: string) {
    // Validate against form definition if one exists for the issue type
    const formDef = await this.prisma.formDefinition.findFirst({
      where: { issueType: dto.type, isActive: true },
      include: { fields: { orderBy: { order: 'asc' } } },
    })

    if (formDef) {
      for (const field of formDef.fields) {
        if (field.required) {
          const value = dto.customFields?.[field.key]
          if (value === undefined || value === null || value === '') {
            throw new BadRequestException(`Missing required custom field: ${field.name}`)
          }
        }
      }
    }

    const requester = await this.prisma.user.findUnique({ where: { id: requesterId } })

    return this.prisma.issue.create({
      data: {
        type: dto.type,
        title: dto.title,
        description: dto.description,
        impact: dto.impact || 'MEDIUM',
        category: dto.category,
        requesterId,
        customFields: dto.customFields ? JSON.stringify(dto.customFields) : null,
        approval: requester?.managerId
          ? {
              create: {
                managerId: requester.managerId,
                status: 'PENDING',
              },
            }
          : undefined,
      },
      include: {
        requester: { select: { id: true, name: true, email: true } },
        approval: { include: { manager: { select: { id: true, name: true } } } },
      },
    })
  }
}

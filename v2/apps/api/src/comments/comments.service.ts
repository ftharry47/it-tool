import { Injectable } from '@nestjs/common'
import { PrismaService } from '../prisma/prisma.service'

@Injectable()
export class CommentsService {
  constructor(private prisma: PrismaService) {}

  create(issueId: string, authorId: string, content: string) {
    return this.prisma.comment.create({
      data: { issueId, authorId, content },
      include: { author: { select: { id: true, name: true } } },
    })
  }

  findByIssue(issueId: string) {
    return this.prisma.comment.findMany({
      where: { issueId },
      include: { author: { select: { id: true, name: true } } },
      orderBy: { createdAt: 'asc' },
    })
  }
}

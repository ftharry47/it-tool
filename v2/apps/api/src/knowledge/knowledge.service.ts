import { Injectable } from '@nestjs/common'
import { PrismaService } from '../prisma/prisma.service'

@Injectable()
export class KnowledgeService {
  constructor(private prisma: PrismaService) {}

  findAll(category?: string, q?: string) {
    return this.prisma.knowledgeArticle.findMany({
      where: {
        category,
        OR: q
          ? [
              { title: { contains: q } },
              { category: { contains: q } },
            ]
          : undefined,
      },
      orderBy: { createdAt: 'desc' },
    })
  }

  findOne(id: string) {
    return this.prisma.knowledgeArticle.findUnique({ where: { id } })
  }

  search(q: string) {
    return this.prisma.knowledgeArticle.findMany({
      where: {
        OR: [
          { title: { contains: q } },
          { content: { contains: q } },
          { category: { contains: q } },
        ],
      },
      take: 5,
      orderBy: { createdAt: 'desc' },
    })
  }
}

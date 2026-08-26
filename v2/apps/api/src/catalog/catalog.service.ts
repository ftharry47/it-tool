import { Injectable } from '@nestjs/common'
import { PrismaService } from '../prisma/prisma.service'

@Injectable()
export class CatalogService {
  constructor(private prisma: PrismaService) {}

  findAll(category?: string) {
    return this.prisma.catalogItem.findMany({
      where: { category },
      orderBy: { createdAt: 'desc' },
    })
  }

  findOne(key: string) {
    return this.prisma.catalogItem.findUnique({ where: { key } })
  }
}

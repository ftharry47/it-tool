import { Injectable, NotFoundException } from '@nestjs/common'
import { PrismaService } from '../prisma/prisma.service'

@Injectable()
export class FormsService {
  constructor(private prisma: PrismaService) {}

  findAll() {
    return this.prisma.formDefinition.findMany({
      include: { fields: { orderBy: { order: 'asc' } } },
      orderBy: { createdAt: 'desc' },
    })
  }

  async findOneByType(issueType: string) {
    const def = await this.prisma.formDefinition.findFirst({
      where: { issueType, isActive: true },
      include: { fields: { orderBy: { order: 'asc' } } },
    })
    if (!def) throw new NotFoundException('Form definition not found')
    return def
  }

  async create(def: { name: string; issueType: string; isActive?: boolean; fields?: any[] }) {
    const { fields, ...data } = def
    return this.prisma.formDefinition.create({
      data: {
        ...data,
        fields: {
          create: fields?.map((f, i) => ({
            name: f.name,
            key: f.key,
            type: f.type,
            required: f.required ?? false,
            options: f.options ? JSON.stringify(f.options) : null,
            order: f.order ?? i,
          })),
        },
      },
      include: { fields: { orderBy: { order: 'asc' } } },
    })
  }

  async update(id: string, def: { name?: string; isActive?: boolean; fields?: any[] }) {
    const { fields, ...data } = def
    await this.prisma.formField.deleteMany({ where: { formDefinitionId: id } })
    return this.prisma.formDefinition.update({
      where: { id },
      data: {
        ...data,
        fields: {
          create: fields?.map((f, i) => ({
            name: f.name,
            key: f.key,
            type: f.type,
            required: f.required ?? false,
            options: f.options ? JSON.stringify(f.options) : null,
            order: f.order ?? i,
          })),
        },
      },
      include: { fields: { orderBy: { order: 'asc' } } },
    })
  }

  remove(id: string) {
    return this.prisma.formDefinition.delete({ where: { id } })
  }
}

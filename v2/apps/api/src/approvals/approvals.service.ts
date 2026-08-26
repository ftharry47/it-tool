import { Injectable, NotFoundException } from '@nestjs/common'
import { PrismaService } from '../prisma/prisma.service'

@Injectable()
export class ApprovalsService {
  constructor(private prisma: PrismaService) {}

  findPendingForManager(managerId: string) {
    return this.prisma.approval.findMany({
      where: { managerId, status: 'PENDING' },
      include: {
        issue: { include: { requester: { select: { id: true, name: true, email: true } } } },
      },
      orderBy: { createdAt: 'desc' },
    })
  }

  async decide(id: string, managerId: string, status: 'APPROVED' | 'REJECTED', comment?: string) {
    const approval = await this.prisma.approval.findUnique({
      where: { id },
    })
    if (!approval || approval.managerId !== managerId) throw new NotFoundException('Approval not found')
    return this.prisma.approval.update({
      where: { id },
      data: { status, comment },
      include: { issue: true },
    })
  }
}

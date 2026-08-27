import { Controller, Get, Post, Delete, Param, Body, UseGuards } from '@nestjs/common'
import { PrismaService } from './prisma/prisma.service'
import { JwtAuthGuard } from './auth/jwt-auth.guard'
import { PermissionsGuard } from './auth/permissions.guard'
import { Permissions } from './auth/decorators/permissions.decorator'

@Controller()
export class AppController {
  constructor(private readonly prisma: PrismaService) {}

  @Get('health')
  async health() {
    const counts = await this.prisma.$transaction([
      this.prisma.user.count(),
      this.prisma.issue.count(),
      this.prisma.configurationItem.count(),
    ])

    return {
      status: 'ok',
      counts: {
        users: counts[0],
        issues: counts[1],
        configurationItems: counts[2],
      },
    }
  }

  @Get('cmdb')
  @UseGuards(JwtAuthGuard)
  async cmdb() {
    const cis = await this.prisma.configurationItem.findMany({
      include: {
        owner: { select: { id: true, name: true, email: true } },
        toRels: { include: { to: { select: { id: true, ciId: true, name: true } } } },
        issues: { include: { issue: { select: { id: true, ticketId: true, status: true, title: true } } } },
      },
      orderBy: { ciId: 'asc' },
    })
    return cis
  }

  @Get('users')
  @UseGuards(JwtAuthGuard, PermissionsGuard)
  @Permissions('admin:all')
  async users() {
    const users = await this.prisma.user.findMany({
      include: {
        userRoles: {
          include: {
            role: { select: { id: true, key: true, name: true } },
          },
        },
      },
      orderBy: { name: 'asc' },
    })
    return users.map((u) => ({ ...u, roles: u.userRoles, permissions: [] }))
  }

  @Get('roles')
  @UseGuards(JwtAuthGuard, PermissionsGuard)
  @Permissions('admin:all')
  async roles() {
    return this.prisma.role.findMany({
      include: {
        permissions: { include: { permission: { select: { id: true, key: true } } } },
      },
      orderBy: { name: 'asc' },
    })
  }

  @Post('user-roles')
  @UseGuards(JwtAuthGuard, PermissionsGuard)
  @Permissions('admin:all')
  async assignRole(@Body() body: { userId: string; roleId: string }) {
    const userRole = await this.prisma.userRole.create({
      data: { userId: body.userId, roleId: body.roleId },
    })
    return userRole
  }

  @Delete('user-roles/:userId/:roleId')
  @UseGuards(JwtAuthGuard, PermissionsGuard)
  @Permissions('admin:all')
  async removeRole(
    @Param('userId') userId: string,
    @Param('roleId') roleId: string,
  ) {
    await this.prisma.userRole.delete({
      where: { userId_roleId: { userId, roleId } },
    })
    return { removed: true }
  }
}

import { Injectable, OnModuleInit } from '@nestjs/common'
import { hash } from 'bcryptjs'
import { PrismaService } from '../prisma/prisma.service'

const ROLES = [
  { key: 'ADMIN', name: 'Administrator', description: 'Full platform access' },
  { key: 'IT', name: 'IT Operator', description: 'Handles incidents and CMDB' },
  { key: 'MANAGER', name: 'Manager', description: 'Approves requests and views reports' },
  { key: 'EMPLOYEE', name: 'Employee', description: 'End-user portal access' },
] as const

const PERMISSIONS = [
  'admin:all',
  'user:read',
  'user:write',
  'issue:read',
  'issue:write',
  'issue:delete',
  'workflow:read',
  'workflow:write',
  'ci:read',
  'ci:write',
] as const

const ROLE_PERMISSIONS: Record<string, string[]> = {
  ADMIN: ['admin:all'],
  IT: ['issue:read', 'issue:write', 'issue:delete', 'workflow:read', 'workflow:write', 'ci:read', 'ci:write', 'user:read'],
  MANAGER: ['issue:read', 'issue:write', 'user:read'],
  EMPLOYEE: ['issue:read', 'issue:write'],
}

@Injectable()
export class UsersService implements OnModuleInit {
  constructor(private readonly prisma: PrismaService) {}

  async onModuleInit() {
    await this.seed()
  }

  async seed() {
    for (const permission of PERMISSIONS) {
      await this.prisma.permission.upsert({
        where: { key: permission },
        update: {},
        create: {
          key: permission,
          description: permission,
        },
      })
    }

    for (const role of ROLES) {
      const createdRole = await this.prisma.role.upsert({
        where: { key: role.key },
        update: {},
        create: {
          key: role.key,
          name: role.name,
          description: role.description,
        },
      })

      const permissionKeys = ROLE_PERMISSIONS[role.key] || []
      for (const key of permissionKeys) {
        const permission = await this.prisma.permission.findUnique({ where: { key } })
        if (permission) {
          await this.prisma.rolePermission.upsert({
            where: {
              roleId_permissionId: {
                roleId: createdRole.id,
                permissionId: permission.id,
              },
            },
            update: {},
            create: {
              roleId: createdRole.id,
              permissionId: permission.id,
            },
          })
        }
      }
    }

    const adminRole = await this.prisma.role.findUnique({ where: { key: 'ADMIN' } })
    if (!adminRole) return

    const existingAdmin = await this.prisma.user.findUnique({
      where: { email: 'admin@work.local' },
    })

    if (!existingAdmin) {
      const passwordHash = await hash('admin-password-123', 10)
      await this.prisma.user.create({
        data: {
          email: 'admin@work.local',
          name: 'Admin User',
          passwordHash,
          userRoles: {
            create: { roleId: adminRole.id },
          },
        },
      })
    }
  }

  async findById(id: string) {
    return this.prisma.user.findUnique({
      where: { id },
      include: {
        userRoles: {
          include: {
            role: {
              include: {
                permissions: {
                  include: {
                    permission: { select: { key: true } },
                  },
                },
              },
            },
          },
        },
      },
    })
  }

  async findByEmail(email: string) {
    return this.prisma.user.findUnique({
      where: { email },
      include: {
        userRoles: {
          include: {
            role: {
              include: {
                permissions: {
                  include: {
                    permission: { select: { key: true } },
                  },
                },
              },
            },
          },
        },
      },
    })
  }
}

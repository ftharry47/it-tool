import { Injectable, UnauthorizedException } from '@nestjs/common'
import { JwtService } from '@nestjs/jwt'
import { compare, hash } from 'bcryptjs'
import { PrismaService } from '../prisma/prisma.service'

export interface RegisterInput {
  email: string
  name: string
  password: string
  managerId?: string
}

export interface UserWithRoles {
  id: string
  email: string
  name: string
  passwordHash: string
  status: string
  managerId: string | null
  userRoles: {
    role: {
      id: string
      key: string
      name: string
      permissions: {
        permission: {
          key: string
        }
      }[]
    }
  }[]
}

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly jwt: JwtService,
  ) {}

  async validateCredentials(email: string, password: string): Promise<UserWithRoles> {
    const user = (await this.prisma.user.findUnique({
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
    })) as UserWithRoles | null

    if (!user) throw new UnauthorizedException('Invalid credentials')

    const valid = await compare(password, user.passwordHash)
    if (!valid) throw new UnauthorizedException('Invalid credentials')

    return user
  }

  login(user: UserWithRoles) {
    const roles = user.userRoles.map((ur) => ur.role.key)
    const permissions = Array.from(
      new Set(user.userRoles.flatMap((ur) => ur.role.permissions.map((p) => p.permission.key))),
    )

    const payload = {
      sub: user.id,
      email: user.email,
      name: user.name,
      roles,
      permissions,
    }

    return {
      access_token: this.jwt.sign(payload),
      user: {
        id: user.id,
        email: user.email,
        name: user.name,
        roles,
        permissions,
      },
    }
  }

  async register(input: RegisterInput) {
    const passwordHash = await hash(input.password, 10)
    const employeeRole = await this.prisma.role.findUnique({ where: { key: 'EMPLOYEE' } })

    const user = await this.prisma.user.create({
      data: {
        email: input.email,
        name: input.name,
        passwordHash,
        managerId: input.managerId ?? null,
        userRoles: employeeRole
          ? {
              create: { roleId: employeeRole.id },
            }
          : undefined,
      },
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

    return this.login(user as unknown as UserWithRoles)
  }
}

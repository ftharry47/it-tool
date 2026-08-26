import { Injectable, UnauthorizedException } from '@nestjs/common'
import { JwtService } from '@nestjs/jwt'
import { User } from '@prisma/client'
import { PrismaService } from '../prisma/prisma.service'

export type AuthenticatedUser = {
  id: string
  email: string
  name: string
  role: string
  managerId: string | null
}

@Injectable()
export class AuthService {
  constructor(private jwt: JwtService, private prisma: PrismaService) {}

  async validateUser(email: string): Promise<AuthenticatedUser | null> {
    const user = await this.prisma.user.findUnique({ where: { email } })
    if (!user) return null
    return this.toUser(user)
  }

  async login(email: string) {
    const user = await this.validateUser(email)
    if (!user) throw new UnauthorizedException('User not found')
    return {
      accessToken: this.jwt.sign(user),
      user,
    }
  }

  toUser(user: User): AuthenticatedUser {
    return {
      id: user.id,
      email: user.email,
      name: user.name,
      role: user.role,
      managerId: user.managerId ?? null,
    }
  }
}

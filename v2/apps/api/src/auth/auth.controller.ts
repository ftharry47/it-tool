import { Controller, Post, Body, Get, Req, UseGuards } from '@nestjs/common'
import { Request } from 'express'
import { IsString, IsNotEmpty } from 'class-validator'
import { AuthService } from './auth.service'
import { JwtAuthGuard } from './jwt.guard'

class LoginDto {
  @IsString()
  @IsNotEmpty()
  email!: string
}

@Controller('auth')
export class AuthController {
  constructor(private auth: AuthService) {}

  @Post('login')
  async login(@Body() body: LoginDto) {
    return this.auth.login(body.email)
  }

  @UseGuards(JwtAuthGuard)
  @Get('me')
  me(@Req() req: Request & { user: any }) {
    return req.user
  }
}

import {
  Body,
  Controller,
  Get,
  Post,
  Request,
  UseGuards,
} from '@nestjs/common'
import { IsEmail, IsOptional, IsString, MinLength } from 'class-validator'
import { AuthService } from './auth.service'
import { JwtAuthGuard } from './jwt-auth.guard'

class LoginDto {
  @IsEmail()
  email!: string

  @IsString()
  @MinLength(6)
  password!: string
}

class RegisterDto {
  @IsEmail()
  email!: string

  @IsString()
  name!: string

  @IsString()
  @MinLength(8)
  password!: string

  @IsString()
  @IsOptional()
  managerId?: string
}

@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService) {}

  @Post('login')
  async login(@Body() dto: LoginDto) {
    const user = await this.auth.validateCredentials(dto.email, dto.password)
    return this.auth.login(user)
  }

  @Post('register')
  async register(@Body() dto: RegisterDto) {
    return this.auth.register(dto)
  }

  @Get('me')
  @UseGuards(JwtAuthGuard)
  me(@Request() req: any) {
    return req.user
  }
}

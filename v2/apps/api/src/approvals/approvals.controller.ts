import { Controller, Get, Post, Body, Param, Req, UseGuards } from '@nestjs/common'
import { Request } from 'express'
import { JwtAuthGuard } from '../auth/jwt.guard'
import { RolesGuard } from '../auth/roles.guard'
import { Roles } from '../auth/roles.decorator'
import { ApprovalsService } from './approvals.service'

class DecideDto {
  status!: 'APPROVED' | 'REJECTED'
  comment?: string
}

@UseGuards(JwtAuthGuard)
@Controller('approvals')
export class ApprovalsController {
  constructor(private approvals: ApprovalsService) {}

  @Get('pending')
  findPending(@Req() req: Request & { user: any }) {
    return this.approvals.findPendingForManager(req.user.id)
  }

  @UseGuards(RolesGuard)
  @Roles('MANAGER')
  @Post(':id/decide')
  decide(@Req() req: Request & { user: any }, @Param('id') id: string, @Body() body: DecideDto) {
    return this.approvals.decide(id, req.user.id, body.status, body.comment)
  }
}

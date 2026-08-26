import { Controller, Get, Post, Body, Param, Query, Req, UseGuards } from '@nestjs/common'
import { Request } from 'express'
import { JwtAuthGuard } from '../auth/jwt.guard'
import { IssuesService } from './issues.service'
import { CreateIssueDto } from './dto/create-issue.dto'

@UseGuards(JwtAuthGuard)
@Controller('issues')
export class IssuesController {
  constructor(private issues: IssuesService) {}

  @Get()
  findAll(@Req() req: Request & { user: any }, @Query('mine') mine?: string) {
    return this.issues.findAll(mine ? req.user.id : undefined)
  }

  @Get(':id')
  findOne(@Param('id') id: string) {
    return this.issues.findOne(id)
  }

  @Post()
  create(@Req() req: Request & { user: any }, @Body() dto: CreateIssueDto) {
    return this.issues.create(dto, req.user.id)
  }
}

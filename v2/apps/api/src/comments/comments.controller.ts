import { Controller, Get, Post, Body, Param, Req, UseGuards } from '@nestjs/common'
import { Request } from 'express'
import { JwtAuthGuard } from '../auth/jwt.guard'
import { CommentsService } from './comments.service'

class CreateCommentDto {
  content!: string
}

@UseGuards(JwtAuthGuard)
@Controller('issues/:issueId/comments')
export class CommentsController {
  constructor(private comments: CommentsService) {}

  @Get()
  findByIssue(@Param('issueId') issueId: string) {
    return this.comments.findByIssue(issueId)
  }

  @Post()
  create(
    @Req() req: Request & { user: any },
    @Param('issueId') issueId: string,
    @Body() body: CreateCommentDto,
  ) {
    return this.comments.create(issueId, req.user.id, body.content)
  }
}

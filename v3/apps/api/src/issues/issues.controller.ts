import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Patch,
  Post,
  Query,
  Request,
  UseGuards,
} from '@nestjs/common'
import { JwtAuthGuard } from '../auth/jwt-auth.guard'
import { Permissions } from '../auth/decorators/permissions.decorator'
import { PermissionsGuard } from '../auth/permissions.guard'
import {
  AddCommentDto,
  CreateIssueDto,
  IssuesService,
  LinkCiDto,
  QueryIssuesDto,
  TransitionIssueDto,
  UpdateIssueDto,
} from './issues.service'

@Controller('issues')
@UseGuards(JwtAuthGuard, PermissionsGuard)
export class IssuesController {
  constructor(private readonly issues: IssuesService) {}

  @Post()
  @Permissions('issue:write')
  create(@Body() dto: CreateIssueDto, @Request() req: any) {
    return this.issues.create(dto, req.user.userId)
  }

  @Get()
  @Permissions('issue:read')
  findAll(@Query() query: QueryIssuesDto, @Request() req: any) {
    return this.issues.findAll(query, req.user)
  }

  @Get(':id')
  @Permissions('issue:read')
  findOne(@Param('id') id: string) {
    return this.issues.findOne(id)
  }

  @Patch(':id')
  @Permissions('issue:write')
  update(@Param('id') id: string, @Body() dto: UpdateIssueDto, @Request() req: any) {
    return this.issues.update(id, dto, req.user)
  }

  @Post(':id/transition')
  @Permissions('issue:write')
  transition(
    @Param('id') id: string,
    @Body() dto: TransitionIssueDto,
    @Request() req: any,
  ) {
    return this.issues.transition(id, dto, req.user)
  }

  @Delete(':id')
  @Permissions('issue:delete')
  remove(@Param('id') id: string, @Request() req: any) {
    return this.issues.remove(id, req.user)
  }

  @Post(':id/comments')
  @Permissions('issue:write')
  addComment(
    @Param('id') id: string,
    @Body() dto: AddCommentDto,
    @Request() req: any,
  ) {
    return this.issues.addComment(id, req.user.userId, dto)
  }

  @Post(':id/cis')
  @Permissions('issue:write')
  linkCi(@Param('id') id: string, @Body() dto: LinkCiDto) {
    return this.issues.linkCi(id, dto)
  }
}

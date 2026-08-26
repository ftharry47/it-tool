import { Controller, Get, Post, Put, Delete, Body, Param, UseGuards } from '@nestjs/common'
import { FormsService } from './forms.service'
import { JwtAuthGuard } from '../auth/jwt.guard'
import { RolesGuard } from '../auth/roles.guard'
import { Roles } from '../auth/roles.decorator'

@Controller('forms')
export class FormsController {
  constructor(private forms: FormsService) {}

  @Get()
  findAll() {
    return this.forms.findAll()
  }

  @Get(':issueType')
  findByType(@Param('issueType') issueType: string) {
    return this.forms.findOneByType(issueType)
  }

  @UseGuards(JwtAuthGuard, RolesGuard)
  @Roles('ADMIN')
  @Post()
  create(@Body() body: any) {
    return this.forms.create(body)
  }

  @UseGuards(JwtAuthGuard, RolesGuard)
  @Roles('ADMIN')
  @Put(':id')
  update(@Param('id') id: string, @Body() body: any) {
    return this.forms.update(id, body)
  }

  @UseGuards(JwtAuthGuard, RolesGuard)
  @Roles('ADMIN')
  @Delete(':id')
  remove(@Param('id') id: string) {
    return this.forms.remove(id)
  }
}

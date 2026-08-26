import { Controller, Get, Param, Query } from '@nestjs/common'
import { KnowledgeService } from './knowledge.service'

@Controller('knowledge')
export class KnowledgeController {
  constructor(private knowledge: KnowledgeService) {}

  @Get()
  findAll(@Query('category') category?: string, @Query('q') q?: string) {
    return this.knowledge.findAll(category, q)
  }

  @Get('search')
  search(@Query('q') q: string) {
    return this.knowledge.search(q || '')
  }

  @Get(':id')
  findOne(@Param('id') id: string) {
    return this.knowledge.findOne(id)
  }
}

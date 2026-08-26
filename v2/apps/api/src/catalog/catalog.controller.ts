import { Controller, Get, Param, Query } from '@nestjs/common'
import { CatalogService } from './catalog.service'

@Controller('catalog')
export class CatalogController {
  constructor(private catalog: CatalogService) {}

  @Get()
  findAll(@Query('category') category?: string) {
    return this.catalog.findAll(category)
  }

  @Get(':key')
  findOne(@Param('key') key: string) {
    return this.catalog.findOne(key)
  }
}

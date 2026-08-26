import { Module } from '@nestjs/common'
import { ConfigModule } from '@nestjs/config'
import { AuthModule } from './auth/auth.module'
import { PrismaModule } from './prisma/prisma.module'
import { IssuesModule } from './issues/issues.module'
import { CatalogModule } from './catalog/catalog.module'
import { KnowledgeModule } from './knowledge/knowledge.module'
import { FormsModule } from './forms/forms.module'
import { ApprovalsModule } from './approvals/approvals.module'
import { CommentsModule } from './comments/comments.module'
import { SeedModule } from './seed/seed.module'

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    PrismaModule,
    AuthModule,
    IssuesModule,
    CatalogModule,
    KnowledgeModule,
    FormsModule,
    ApprovalsModule,
    CommentsModule,
    SeedModule,
  ],
})
export class AppModule {}

import { Module } from '@nestjs/common'
import { ConfigModule } from '@nestjs/config'
import { AppController } from './app.controller'
import { AuthModule } from './auth/auth.module'
import { IssuesModule } from './issues/issues.module'
import { PrismaModule } from './prisma/prisma.module'
import { UsersModule } from './users/users.module'
import { WorkflowsModule } from './workflows/workflows.module'
import { SlaModule } from './sla/sla.module'

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env', '../.env', '../../.env'],
    }),
    PrismaModule,
    AuthModule,
    UsersModule,
    IssuesModule,
    WorkflowsModule,
    SlaModule,
  ],
  controllers: [AppController],
  providers: [],
})
export class AppModule {}

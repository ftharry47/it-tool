import { Module } from '@nestjs/common'
import { IssuesController } from './issues.controller'
import { IssuesService } from './issues.service'
import { WorkflowsModule } from '../workflows/workflows.module'
import { SlaModule } from '../sla/sla.module'

@Module({
  imports: [WorkflowsModule, SlaModule],
  controllers: [IssuesController],
  providers: [IssuesService],
  exports: [IssuesService],
})
export class IssuesModule {}

import { IsString, IsNotEmpty, IsOptional, IsIn } from 'class-validator'

export class CreateIssueDto {
  @IsString()
  @IsNotEmpty()
  title!: string

  @IsString()
  @IsNotEmpty()
  description!: string

  @IsIn(['LOW', 'MEDIUM', 'HIGH'])
  @IsOptional()
  impact?: string

  @IsString()
  @IsNotEmpty()
  category!: string

  @IsIn(['INCIDENT', 'REQUEST'])
  type!: string

  @IsOptional()
  customFields?: Record<string, any>
}

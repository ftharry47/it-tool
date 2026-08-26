"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.IssuesService = void 0;
const common_1 = require("@nestjs/common");
const prisma_service_1 = require("../prisma/prisma.service");
let IssuesService = class IssuesService {
    constructor(prisma) {
        this.prisma = prisma;
    }
    async findAll(requesterId, assigneeId) {
        return this.prisma.issue.findMany({
            where: { requesterId, assigneeId },
            orderBy: { createdAt: 'desc' },
            include: { requester: { select: { id: true, name: true, email: true } }, comments: true },
        });
    }
    async findOne(id) {
        const issue = await this.prisma.issue.findUnique({
            where: { id },
            include: {
                requester: { select: { id: true, name: true, email: true } },
                assignee: { select: { id: true, name: true, email: true } },
                comments: { include: { author: { select: { id: true, name: true } } }, orderBy: { createdAt: 'asc' } },
                approval: { include: { manager: { select: { id: true, name: true } } } },
            },
        });
        if (!issue)
            throw new common_1.NotFoundException('Issue not found');
        return issue;
    }
    async create(dto, requesterId) {
        const formDef = await this.prisma.formDefinition.findFirst({
            where: { issueType: dto.type, isActive: true },
            include: { fields: { orderBy: { order: 'asc' } } },
        });
        if (formDef) {
            for (const field of formDef.fields) {
                if (field.required) {
                    const value = dto.customFields?.[field.key];
                    if (value === undefined || value === null || value === '') {
                        throw new common_1.BadRequestException(`Missing required custom field: ${field.name}`);
                    }
                }
            }
        }
        const requester = await this.prisma.user.findUnique({ where: { id: requesterId } });
        return this.prisma.issue.create({
            data: {
                type: dto.type,
                title: dto.title,
                description: dto.description,
                impact: dto.impact || 'MEDIUM',
                category: dto.category,
                requesterId,
                customFields: dto.customFields ? JSON.stringify(dto.customFields) : null,
                approval: requester?.managerId
                    ? {
                        create: {
                            managerId: requester.managerId,
                            status: 'PENDING',
                        },
                    }
                    : undefined,
            },
            include: {
                requester: { select: { id: true, name: true, email: true } },
                approval: { include: { manager: { select: { id: true, name: true } } } },
            },
        });
    }
};
exports.IssuesService = IssuesService;
exports.IssuesService = IssuesService = __decorate([
    (0, common_1.Injectable)(),
    __metadata("design:paramtypes", [prisma_service_1.PrismaService])
], IssuesService);
//# sourceMappingURL=issues.service.js.map
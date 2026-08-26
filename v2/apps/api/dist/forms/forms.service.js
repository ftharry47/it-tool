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
exports.FormsService = void 0;
const common_1 = require("@nestjs/common");
const prisma_service_1 = require("../prisma/prisma.service");
let FormsService = class FormsService {
    constructor(prisma) {
        this.prisma = prisma;
    }
    findAll() {
        return this.prisma.formDefinition.findMany({
            include: { fields: { orderBy: { order: 'asc' } } },
            orderBy: { createdAt: 'desc' },
        });
    }
    async findOneByType(issueType) {
        const def = await this.prisma.formDefinition.findFirst({
            where: { issueType, isActive: true },
            include: { fields: { orderBy: { order: 'asc' } } },
        });
        if (!def)
            throw new common_1.NotFoundException('Form definition not found');
        return def;
    }
    async create(def) {
        const { fields, ...data } = def;
        return this.prisma.formDefinition.create({
            data: {
                ...data,
                fields: {
                    create: fields?.map((f, i) => ({
                        name: f.name,
                        key: f.key,
                        type: f.type,
                        required: f.required ?? false,
                        options: f.options ? JSON.stringify(f.options) : null,
                        order: f.order ?? i,
                    })),
                },
            },
            include: { fields: { orderBy: { order: 'asc' } } },
        });
    }
    async update(id, def) {
        const { fields, ...data } = def;
        await this.prisma.formField.deleteMany({ where: { formDefinitionId: id } });
        return this.prisma.formDefinition.update({
            where: { id },
            data: {
                ...data,
                fields: {
                    create: fields?.map((f, i) => ({
                        name: f.name,
                        key: f.key,
                        type: f.type,
                        required: f.required ?? false,
                        options: f.options ? JSON.stringify(f.options) : null,
                        order: f.order ?? i,
                    })),
                },
            },
            include: { fields: { orderBy: { order: 'asc' } } },
        });
    }
    remove(id) {
        return this.prisma.formDefinition.delete({ where: { id } });
    }
};
exports.FormsService = FormsService;
exports.FormsService = FormsService = __decorate([
    (0, common_1.Injectable)(),
    __metadata("design:paramtypes", [prisma_service_1.PrismaService])
], FormsService);
//# sourceMappingURL=forms.service.js.map
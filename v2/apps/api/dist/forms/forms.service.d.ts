import { PrismaService } from '../prisma/prisma.service';
export declare class FormsService {
    private prisma;
    constructor(prisma: PrismaService);
    findAll(): import(".prisma/client").Prisma.PrismaPromise<({
        fields: {
            id: string;
            name: string;
            createdAt: Date;
            updatedAt: Date;
            type: string;
            order: number;
            formDefinitionId: string;
            key: string;
            required: boolean;
            options: string | null;
        }[];
    } & {
        id: string;
        name: string;
        createdAt: Date;
        updatedAt: Date;
        issueType: string;
        isActive: boolean;
    })[]>;
    findOneByType(issueType: string): Promise<{
        fields: {
            id: string;
            name: string;
            createdAt: Date;
            updatedAt: Date;
            type: string;
            order: number;
            formDefinitionId: string;
            key: string;
            required: boolean;
            options: string | null;
        }[];
    } & {
        id: string;
        name: string;
        createdAt: Date;
        updatedAt: Date;
        issueType: string;
        isActive: boolean;
    }>;
    create(def: {
        name: string;
        issueType: string;
        isActive?: boolean;
        fields?: any[];
    }): Promise<{
        fields: {
            id: string;
            name: string;
            createdAt: Date;
            updatedAt: Date;
            type: string;
            order: number;
            formDefinitionId: string;
            key: string;
            required: boolean;
            options: string | null;
        }[];
    } & {
        id: string;
        name: string;
        createdAt: Date;
        updatedAt: Date;
        issueType: string;
        isActive: boolean;
    }>;
    update(id: string, def: {
        name?: string;
        isActive?: boolean;
        fields?: any[];
    }): Promise<{
        fields: {
            id: string;
            name: string;
            createdAt: Date;
            updatedAt: Date;
            type: string;
            order: number;
            formDefinitionId: string;
            key: string;
            required: boolean;
            options: string | null;
        }[];
    } & {
        id: string;
        name: string;
        createdAt: Date;
        updatedAt: Date;
        issueType: string;
        isActive: boolean;
    }>;
    remove(id: string): import(".prisma/client").Prisma.Prisma__FormDefinitionClient<{
        id: string;
        name: string;
        createdAt: Date;
        updatedAt: Date;
        issueType: string;
        isActive: boolean;
    }, never, import("@prisma/client/runtime/library").DefaultArgs>;
}

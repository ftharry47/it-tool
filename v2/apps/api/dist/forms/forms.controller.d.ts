import { FormsService } from './forms.service';
export declare class FormsController {
    private forms;
    constructor(forms: FormsService);
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
    findByType(issueType: string): Promise<{
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
    create(body: any): Promise<{
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
    update(id: string, body: any): Promise<{
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

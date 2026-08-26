import { PrismaService } from '../prisma/prisma.service';
export declare class CatalogService {
    private prisma;
    constructor(prisma: PrismaService);
    findAll(category?: string): import(".prisma/client").Prisma.PrismaPromise<{
        id: string;
        name: string;
        createdAt: Date;
        updatedAt: Date;
        description: string;
        category: string;
        issueType: string;
        key: string;
        icon: string | null;
        formDefId: string | null;
    }[]>;
    findOne(key: string): import(".prisma/client").Prisma.Prisma__CatalogItemClient<{
        id: string;
        name: string;
        createdAt: Date;
        updatedAt: Date;
        description: string;
        category: string;
        issueType: string;
        key: string;
        icon: string | null;
        formDefId: string | null;
    } | null, null, import("@prisma/client/runtime/library").DefaultArgs>;
}

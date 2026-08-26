export declare class CreateIssueDto {
    title: string;
    description: string;
    impact?: string;
    category: string;
    type: string;
    customFields?: Record<string, any>;
}

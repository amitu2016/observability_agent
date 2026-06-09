import axios from 'axios';

const CALLER_SERVICE_URL = 'http://localhost:8081';
const DOWNSTREAM_SERVICE_URL = 'http://localhost:8082';
const TRIAGE_AGENT_URL = 'http://localhost:8084';

export interface Account {
  accountId: string;
  balance: number;
}

export interface Transfer {
  transferId: string;
  status: string;
  message?: string;
}

export interface TransferRequest {
  fromAccount: string;
  toAccount: string;
  amount: number;
}

export interface TriageStep {
  stepName: string;
  status: string;
  summary: string;
  toolCalls: any[];
  durationMs: number;
}

export interface TriageReport {
  question: string;
  service: string;
  timeWindow: string;
  rootCause: string;
  confidence: number;
  steps: TriageStep[];
  deepLinks: string[];
  notChecked: string[];
}

export interface InvestigationJob {
  investigationId: string;
  question: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  createdAt: string;
  completedAt?: string;
  report?: TriageReport;
}

export const api = {
  getAccounts: () => axios.get<Account[]>(`${DOWNSTREAM_SERVICE_URL}/accounts`).then(res => res.data),
  createAccount: (accountId: string, initialBalance: number) => 
    axios.post(`${DOWNSTREAM_SERVICE_URL}/accounts`, { accountId, initialBalance }).then(res => res.data),
  
  getTransfers: () => axios.get<Transfer[]>(`${CALLER_SERVICE_URL}/transfers`).then(res => res.data),
  initiateTransfer: (request: TransferRequest) => 
    axios.post<Transfer>(`${CALLER_SERVICE_URL}/transfers`, request).then(res => res.data),

  investigate: (question: string) => 
    axios.post<InvestigationJob>(`${TRIAGE_AGENT_URL}/api/triage/investigate`, { question }).then(res => res.data),
  getInvestigation: (id: string) => 
    axios.get<InvestigationJob>(`${TRIAGE_AGENT_URL}/api/triage/investigation/${id}`).then(res => res.data),
};

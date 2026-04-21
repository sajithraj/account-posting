import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useMutation} from '@tanstack/react-query';
import {getErrorMessage, postingApi} from '../api/postingApi';
import type {AccountPostingRequest, CreditDebitIndicator} from '../types/posting';

const empty: AccountPostingRequest = {
    sourceReferenceId: '',
    endToEndReferenceId: '',
    sourceName: '',
    requestType: '',
    amount: {value: '', currencyCode: ''},
    creditDebitIndicator: 'CREDIT',
    debtorAccount: '',
    creditorAccount: '',
    requestedExecutionDate: '',
    remittanceInformation: '',
};

export default function CreatePostingPage() {
    const navigate = useNavigate();
    const [form, setForm] = useState<AccountPostingRequest>(empty);
    const [errors, setErrors] = useState<string[]>([]);

    const mutation = useMutation({
        mutationFn: postingApi.create,
        onSuccess: () => navigate('/'),
        onError: err => setErrors([getErrorMessage(err)]),
    });

    const set = (field: keyof Omit<AccountPostingRequest, 'amount'>) =>
        (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
            setForm(f => ({...f, [field]: e.target.value}));

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setErrors([]);
        mutation.mutate(form);
    };

    return (
        <div style={{maxWidth: 600}}>
            <h2>New Account Posting</h2>

            {errors.map((e, i) => (
                <p key={i} style={{color: 'red'}}>{e}</p>
            ))}

            <form onSubmit={handleSubmit} style={{display: 'flex', flexDirection: 'column', gap: 12}}>
                <FormField label="Source Reference ID *">
                    <input required value={form.sourceReferenceId} onChange={set('sourceReferenceId')}/>
                </FormField>
                <FormField label="End-to-End Reference ID *">
                    <input required value={form.endToEndReferenceId} onChange={set('endToEndReferenceId')}/>
                </FormField>
                <FormField label="Source Name *">
                    <input required value={form.sourceName} onChange={set('sourceName')}
                           placeholder="e.g. IMX"/>
                </FormField>
                <FormField label="Request Type *">
                    <input required value={form.requestType} onChange={set('requestType')}
                           placeholder="e.g. IMX_CBS_GL"/>
                </FormField>
                <FormField label="Amount *">
                    <input required type="number" step="0.0001" min="0.0001"
                           value={form.amount.value}
                           onChange={e => setForm(f => ({...f, amount: {...f.amount, value: e.target.value}}))}/>
                </FormField>
                <FormField label="Currency *">
                    <input required value={form.amount.currencyCode} maxLength={3} placeholder="USD"
                           onChange={e => setForm(f => ({...f, amount: {...f.amount, currencyCode: e.target.value}}))}/>
                </FormField>
                <FormField label="Credit / Debit *">
                    <select value={form.creditDebitIndicator} onChange={e => setForm(f => ({
                        ...f,
                        creditDebitIndicator: e.target.value as CreditDebitIndicator,
                    }))}>
                        <option value="CREDIT">CREDIT</option>
                        <option value="DEBIT">DEBIT</option>
                    </select>
                </FormField>
                <FormField label="Debtor Account *">
                    <input required value={form.debtorAccount} onChange={set('debtorAccount')}/>
                </FormField>
                <FormField label="Creditor Account *">
                    <input required value={form.creditorAccount} onChange={set('creditorAccount')}/>
                </FormField>
                <FormField label="Requested Execution Date *">
                    <input required type="date" value={form.requestedExecutionDate}
                           onChange={set('requestedExecutionDate')}/>
                </FormField>
                <FormField label="Remittance Information">
                    <input value={form.remittanceInformation ?? ''} onChange={set('remittanceInformation')}/>
                </FormField>

                <button type="submit" disabled={mutation.isPending}
                        style={{
                            padding: '10px 20px',
                            backgroundColor: '#2980b9',
                            color: 'white',
                            border: 'none',
                            borderRadius: 4,
                            cursor: 'pointer',
                        }}>
                    {mutation.isPending ? 'Submitting...' : 'Submit Posting'}
                </button>
            </form>
        </div>
    );
}

function FormField({label, children}: { label: string; children: React.ReactNode }) {
    return (
        <label style={{display: 'flex', flexDirection: 'column', gap: 4}}>
            <span style={{fontSize: 13, fontWeight: 500}}>{label}</span>
            {children}
        </label>
    );
}

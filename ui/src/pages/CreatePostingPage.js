import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { getErrorMessage, postingApi } from '../api/postingApi';
const empty = {
    sourceReferenceId: '',
    endToEndReferenceId: '',
    sourceName: '',
    requestType: '',
    amount: { value: '', currencyCode: '' },
    creditDebitIndicator: 'CREDIT',
    debtorAccount: '',
    creditorAccount: '',
    requestedExecutionDate: '',
    remittanceInformation: '',
};
export default function CreatePostingPage() {
    const navigate = useNavigate();
    const [form, setForm] = useState(empty);
    const [errors, setErrors] = useState([]);
    const mutation = useMutation({
        mutationFn: postingApi.create,
        onSuccess: () => navigate('/'),
        onError: err => setErrors([getErrorMessage(err)]),
    });
    const set = (field) => (e) => setForm(f => ({ ...f, [field]: e.target.value }));
    const handleSubmit = (e) => {
        e.preventDefault();
        setErrors([]);
        mutation.mutate(form);
    };
    return (_jsxs("div", { style: { maxWidth: 600 }, children: [_jsx("h2", { children: "New Account Posting" }), errors.map((e, i) => (_jsx("p", { style: { color: 'red' }, children: e }, i))), _jsxs("form", { onSubmit: handleSubmit, style: { display: 'flex', flexDirection: 'column', gap: 12 }, children: [_jsx(FormField, { label: "Source Reference ID *", children: _jsx("input", { required: true, value: form.sourceReferenceId, onChange: set('sourceReferenceId') }) }), _jsx(FormField, { label: "End-to-End Reference ID *", children: _jsx("input", { required: true, value: form.endToEndReferenceId, onChange: set('endToEndReferenceId') }) }), _jsx(FormField, { label: "Source Name *", children: _jsx("input", { required: true, value: form.sourceName, onChange: set('sourceName'), placeholder: "e.g. IMX" }) }), _jsx(FormField, { label: "Request Type *", children: _jsx("input", { required: true, value: form.requestType, onChange: set('requestType'), placeholder: "e.g. IMX_CBS_GL" }) }), _jsx(FormField, { label: "Amount *", children: _jsx("input", { required: true, type: "number", step: "0.0001", min: "0.0001", value: form.amount.value, onChange: e => setForm(f => ({ ...f, amount: { ...f.amount, value: e.target.value } })) }) }), _jsx(FormField, { label: "Currency *", children: _jsx("input", { required: true, value: form.amount.currencyCode, maxLength: 3, placeholder: "USD", onChange: e => setForm(f => ({ ...f, amount: { ...f.amount, currencyCode: e.target.value } })) }) }), _jsx(FormField, { label: "Credit / Debit *", children: _jsxs("select", { value: form.creditDebitIndicator, onChange: e => setForm(f => ({
                                ...f,
                                creditDebitIndicator: e.target.value,
                            })), children: [_jsx("option", { value: "CREDIT", children: "CREDIT" }), _jsx("option", { value: "DEBIT", children: "DEBIT" })] }) }), _jsx(FormField, { label: "Debtor Account *", children: _jsx("input", { required: true, value: form.debtorAccount, onChange: set('debtorAccount') }) }), _jsx(FormField, { label: "Creditor Account *", children: _jsx("input", { required: true, value: form.creditorAccount, onChange: set('creditorAccount') }) }), _jsx(FormField, { label: "Requested Execution Date *", children: _jsx("input", { required: true, type: "date", value: form.requestedExecutionDate, onChange: set('requestedExecutionDate') }) }), _jsx(FormField, { label: "Remittance Information", children: _jsx("input", { value: form.remittanceInformation ?? '', onChange: set('remittanceInformation') }) }), _jsx("button", { type: "submit", disabled: mutation.isPending, style: {
                            padding: '10px 20px',
                            backgroundColor: '#2980b9',
                            color: 'white',
                            border: 'none',
                            borderRadius: 4,
                            cursor: 'pointer',
                        }, children: mutation.isPending ? 'Submitting...' : 'Submit Posting' })] })] }));
}
function FormField({ label, children }) {
    return (_jsxs("label", { style: { display: 'flex', flexDirection: 'column', gap: 4 }, children: [_jsx("span", { style: { fontSize: 13, fontWeight: 500 }, children: label }), children] }));
}

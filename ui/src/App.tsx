import React, { useState, useEffect } from 'react'
import { api } from './api'
import type { Account, Transfer, InvestigationJob } from './api'
import { AgentUI } from './components/AgentUI'
import './App.css'

function App() {
  const [activeTab, setActiveTab] = useState<'banking' | 'agent'>('banking')
  const [accounts, setAccounts] = useState<Account[]>([])
  const [transfers, setTransfers] = useState<Transfer[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Agent State (lifted to persist across tab switches)
  const [currentJob, setCurrentJob] = useState<InvestigationJob | null>(null)

  // Form states
  const [newAccountId, setNewAccountId] = useState('')
  const [initialBalance, setInitialBalance] = useState(100)
  
  const [fromAccount, setFromAccount] = useState('')
  const [toAccount, setToAccount] = useState('')
  const [amount, setAmount] = useState(10)

  const refreshData = async () => {
    setLoading(true)
    try {
      const [accs, trans] = await Promise.all([
        api.getAccounts(),
        api.getTransfers()
      ])
      setAccounts(accs)
      setTransfers(trans)
      setError(null)
    } catch (err: any) {
      console.error(err)
      setError('Failed to fetch data. Are the services running?')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refreshData()
    const interval = setInterval(refreshData, 5000)
    return () => clearInterval(interval)
  }, [])

  const handleCreateAccount = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      await api.createAccount(newAccountId, initialBalance)
      setNewAccountId('')
      refreshData()
    } catch (err) {
      setError('Failed to create account')
    }
  }

  const handleTransfer = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      await api.initiateTransfer({ fromAccount, toAccount, amount })
      setFromAccount('')
      setToAccount('')
      setAmount(10)
      refreshData()
    } catch (err) {
      setError('Failed to initiate transfer')
    }
  }

  return (
    <div className="container">
      <header>
        <h1>Observability Dashboard</h1>
        <nav className="tabs">
          <button 
            className={activeTab === 'banking' ? 'active' : ''} 
            onClick={() => setActiveTab('banking')}
          >
            Banking
          </button>
          <button 
            className={activeTab === 'agent' ? 'active' : ''} 
            onClick={() => setActiveTab('agent')}
          >
            Triage Agent
          </button>
        </nav>
        {loading && <span className="loader">Refreshing...</span>}
      </header>

      {error && <div className="error-banner">{error}</div>}

      <div style={{ display: activeTab === 'banking' ? 'block' : 'none' }}>
        <div className="grid">
          <section className="card">
            <h2>Create Account</h2>
            <form onSubmit={handleCreateAccount}>
              <div className="form-group">
                <label>Account ID</label>
                <input 
                  type="text" 
                  value={newAccountId} 
                  onChange={(e) => setNewAccountId(e.target.value)} 
                  required 
                />
              </div>
              <div className="form-group">
                <label>Initial Balance</label>
                <input 
                  type="number" 
                  value={initialBalance} 
                  onChange={(e) => setInitialBalance(Number(e.target.value))} 
                  required 
                />
              </div>
              <button type="submit">Create</button>
            </form>
          </section>

          <section className="card">
            <h2>Initiate Transfer</h2>
            <form onSubmit={handleTransfer}>
              <div className="form-group">
                <label>From Account</label>
                <input 
                  type="text" 
                  value={fromAccount} 
                  onChange={(e) => setFromAccount(e.target.value)} 
                  required 
                />
              </div>
              <div className="form-group">
                <label>To Account</label>
                <input 
                  type="text" 
                  value={toAccount} 
                  onChange={(e) => setToAccount(e.target.value)} 
                  required 
                />
              </div>
              <div className="form-group">
                <label>Amount</label>
                <input 
                  type="number" 
                  value={amount} 
                  onChange={(e) => setAmount(Number(e.target.value))} 
                  required 
                />
              </div>
              <button type="submit">Transfer</button>
            </form>
          </section>
        </div>

        <div className="grid">
          <section className="card">
            <h2>Accounts</h2>
            <table>
              <thead>
                <tr>
                  <th>Account ID</th>
                  <th>Balance</th>
                </tr>
              </thead>
              <tbody>
                {accounts.map(acc => (
                  <tr key={acc.accountId}>
                    <td>{acc.accountId}</td>
                    <td>${acc.balance.toFixed(2)}</td>
                  </tr>
                ))}
                {accounts.length === 0 && <tr><td colSpan={2}>No accounts found</td></tr>}
              </tbody>
            </table>
          </section>

          <section className="card">
            <h2>Recent Transfers</h2>
            <table>
              <thead>
                <tr>
                  <th>Transfer ID</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {[...transfers].reverse().map(t => (
                  <tr key={t.transferId}>
                    <td className="mono">{t.transferId.substring(0, 8)}...</td>
                    <td>
                      <span className={`status-badge ${t.status.toLowerCase()}`}>
                        {t.status}
                      </span>
                    </td>
                  </tr>
                ))}
                {transfers.length === 0 && <tr><td colSpan={2}>No transfers found</td></tr>}
              </tbody>
            </table>
          </section>
        </div>
      </div>

      <div style={{ display: activeTab === 'agent' ? 'block' : 'none' }}>
        <AgentUI 
          currentJob={currentJob} 
          onJobStarted={setCurrentJob} 
          onJobUpdated={setCurrentJob} 
        />
      </div>
    </div>
  )
}

export default App

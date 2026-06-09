import React, { useState, useEffect } from 'react'
import { api } from '../api'
import type { InvestigationJob } from '../api'

interface AgentUIProps {
  currentJob: InvestigationJob | null;
  onJobStarted: (job: InvestigationJob) => void;
  onJobUpdated: (job: InvestigationJob) => void;
}

export const AgentUI: React.FC<AgentUIProps> = ({ currentJob, onJobStarted, onJobUpdated }) => {
  const [question, setQuestion] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [polling, setPolling] = useState(false)

  const handleInvestigate = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    try {
      const job = await api.investigate(question)
      onJobStarted(job)
      setPolling(true)
    } catch (err) {
      setError('Failed to start investigation')
    }
  }

  useEffect(() => {
    let intervalId: any
    // Resume polling if the job is still active
    const shouldPoll = currentJob && (currentJob.status === 'PENDING' || currentJob.status === 'RUNNING');
    
    if (shouldPoll) {
      setPolling(true)
      intervalId = setInterval(async () => {
        try {
          const updatedJob = await api.getInvestigation(currentJob.investigationId)
          onJobUpdated(updatedJob)
          if (updatedJob.status === 'COMPLETED' || updatedJob.status === 'FAILED') {
            setPolling(false)
          }
        } catch (err) {
          setError('Error polling investigation status')
          setPolling(false)
        }
      }, 2000)
    } else {
      setPolling(false)
    }
    return () => clearInterval(intervalId)
  }, [currentJob?.investigationId, currentJob?.status])

  return (
    <div className="agent-container">
      <section className="card">
        <h2>Triage Agent</h2>
        <form onSubmit={handleInvestigate}>
          <div className="form-group">
            <label>Describe the symptom or ask a question</label>
            <textarea 
              value={question} 
              onChange={(e) => setQuestion(e.target.value)} 
              placeholder="e.g., High error rate on caller-service in the last 15 minutes"
              required 
            />
          </div>
          <button type="submit" disabled={polling}>
            {polling ? 'Investigating...' : 'Start Investigation'}
          </button>
        </form>
      </section>

      {error && <div className="error-banner">{error}</div>}

      {currentJob && (
        <section className="card report-card">
          <div className="report-header">
            <h3>Investigation: {currentJob.status}</h3>
            <span className="timestamp">Started: {new Date(currentJob.createdAt).toLocaleString()}</span>
          </div>

          {!currentJob.report && currentJob.status !== 'FAILED' && (
            <div className="loading-report">
              <div className="spinner"></div>
              <p>The agent is correlating metrics, traces, and logs...</p>
            </div>
          )}

          {currentJob.report && (
            <div className="report-content">
              <div className="report-summary">
                <div className="summary-item">
                  <strong>Service:</strong> {currentJob.report.service}
                </div>
                <div className="summary-item">
                  <strong>Confidence:</strong> {(currentJob.report.confidence * 100).toFixed(0)}%
                </div>
              </div>

              <div className="root-cause">
                <h4>Root Cause Hypothesis</h4>
                <p>{currentJob.report.rootCause}</p>
              </div>

              <div className="steps-timeline">
                <h4>Investigation Steps</h4>
                <ul className="steps-list">
                  {currentJob.report.steps.map((step, i) => (
                    <li key={i} className={`step ${step.status.toLowerCase()}`}>
                      <span className="step-name">{step.stepName}</span>
                      <p className="step-summary">{step.summary}</p>
                    </li>
                  ))}
                </ul>
              </div>

              {currentJob.report.deepLinks.length > 0 && (
                <div className="deep-links">
                  <h4>Evidence & Deep Links</h4>
                  <div className="links-grid">
                    {currentJob.report.deepLinks.map((link, i) => (
                      <a key={i} href={link} target="_blank" rel="noreferrer" className="deep-link">
                        View in Grafana
                      </a>
                    ))}
                  </div>
                </div>
              )}

              {currentJob.report.notChecked.length > 0 && (
                <div className="not-checked">
                  <h4>Not Checked</h4>
                  <ul>
                    {currentJob.report.notChecked.map((item, i) => (
                      <li key={i}>{item}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </section>
      )}
    </div>
  )
}

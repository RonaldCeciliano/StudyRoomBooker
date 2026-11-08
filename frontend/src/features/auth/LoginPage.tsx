import { type FormEvent, useState } from 'react'
import { Navigate } from 'react-router'
import { useRequestMagicLink, useSession } from './api'
import './auth.css'

export function LoginPage() {
  const session = useSession()
  const requestLink = useRequestMagicLink()
  const [email, setEmail] = useState('')

  if (session.data?.authenticated) {
    return <Navigate to="/" replace />
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    requestLink.mutate(email)
  }

  return (
    <>
      <h1 className="page-title">Sign In</h1>
      <section className="section auth-card" aria-labelledby="sign-in-heading">
        <h2 id="sign-in-heading" className="section-title">
          Get a sign-in link
        </h2>
        {requestLink.isSuccess ? (
          <div role="status">
            <p>If that email belongs to a group member, a sign-in link is on its way.</p>
            <p className="secondary-text">The link works once and expires in 15 minutes.</p>
            <button type="button" className="button button-plain" onClick={() => requestLink.reset()}>
              Use a different email
            </button>
          </div>
        ) : (
          <form className="auth-form" onSubmit={submit}>
            <p className="secondary-text">We’ll email you a link. No password needed.</p>
            <label className="field-label" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              className="text-field"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
            {requestLink.isError && (
              <p className="error-text" role="alert">
                Couldn’t send the link. {requestLink.error.message}
              </p>
            )}
            <button type="submit" className="button button-prominent" disabled={requestLink.isPending}>
              {requestLink.isPending ? 'Sending…' : 'Email me a link'}
            </button>
          </form>
        )}
      </section>
    </>
  )
}

import { Link, useNavigate, useSearchParams } from 'react-router'
import { useSignIn } from './api'
import './auth.css'

/**
 * Landing page for the emailed link. Email scanners open links automatically, so
 * opening this page does nothing; only pressing Continue (a POST) uses the token.
 */
export function MagicLinkPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const signIn = useSignIn()
  const navigate = useNavigate()

  function continueSignIn() {
    if (token) {
      signIn.mutate(token, { onSuccess: () => navigate('/', { replace: true }) })
    }
  }

  return (
    <>
      <h1 className="page-title">Sign In</h1>
      <section className="section auth-card" aria-labelledby="continue-heading">
        <h2 id="continue-heading" className="section-title">
          Continue to StudyRoomBooker
        </h2>
        {!token ? (
          <p className="error-text" role="alert">
            This link is missing its sign-in token. <Link to="/login">Request a new link</Link>.
          </p>
        ) : signIn.isError ? (
          <p className="error-text" role="alert">
            This sign-in link is invalid, expired, or already used. <Link to="/login">Request a new link</Link>.
          </p>
        ) : (
          <div className="auth-form">
            <p className="secondary-text">Press Continue to finish signing in on this device.</p>
            <button
              type="button"
              className="button button-prominent"
              onClick={continueSignIn}
              disabled={signIn.isPending}
            >
              {signIn.isPending ? 'Signing in…' : 'Continue'}
            </button>
          </div>
        )}
      </section>
    </>
  )
}

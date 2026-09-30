import { Link } from 'react-router'

export function ErrorPage() {
  return (
    <>
      <h1 className="page-title">Something Went Wrong</h1>
      <section className="section">
        <p className="secondary-text">
          An unexpected error occurred. Try again, or go back to the dashboard.
        </p>
        <p>
          <Link to="/" className="button button-prominent">
            Back to Dashboard
          </Link>
        </p>
      </section>
    </>
  )
}

import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <>
      <h1 className="page-title">Page Not Found</h1>
      <section className="section">
        <p className="secondary-text">This page does not exist.</p>
        <p>
          <Link to="/" className="button button-prominent">
            Back to Dashboard
          </Link>
        </p>
      </section>
    </>
  )
}

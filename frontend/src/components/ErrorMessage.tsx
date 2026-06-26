interface ErrorMessageProps {
  message: string;
}

export function ErrorMessage({ message }: ErrorMessageProps) {
  return (
    <div className="error-message" role="alert">
      <span className="error-icon" aria-hidden="true">⚠</span>
      <span>{message}</span>
    </div>
  );
}

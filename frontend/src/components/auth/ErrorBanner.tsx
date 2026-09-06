interface ErrorBannerProps {
    message: string | null;
}

export default function ErrorBanner({ message }: ErrorBannerProps) {
    if (!message) return null;

    return (
        <div className="mb-6 border-l-2 border-red-500 bg-red-50/60 px-3 py-2 text-sm text-red-700">
            {message}
        </div>
    );
}

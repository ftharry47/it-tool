import { redirect } from 'next/navigation'

export default function SubmitRedirectPage({
  searchParams,
}: {
  searchParams: { [key: string]: string | string[] | undefined }
}) {
  const type = searchParams.type
  const q = type ? `?type=${encodeURIComponent(Array.isArray(type) ? type[0] : type)}` : ''
  redirect(`/dashboard/submit${q}`)
}

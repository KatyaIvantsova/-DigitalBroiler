import { Suspense } from "react"
import { LoginForm } from "./login-form"

export const metadata = {
  title: "Вход — АгроКонтроль",
}

export default function LoginPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-4">
      <Suspense>
        <LoginForm />
      </Suspense>
    </main>
  )
}

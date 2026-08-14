import { useState } from 'react'
import { Button, Card, Form, Input, Message } from '@arco-design/web-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { login } from '@/api/auth'
import { userStore } from '@/stores/user'

const FormItem = Form.Item

export default function LoginPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm()

  async function handleLogin() {
    const values = await form.validate()
    if (!values.username || !values.password) {
      return
    }
    setLoading(true)
    try {
      const result = await login(values.username, values.password)
      userStore.save(result.token, result.user)
      Message.success(t('login.success'))
      navigate('/')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <Card className="login-card" title={t('login.title')}>
        <Form
          form={form}
          layout="vertical"
          initialValues={{ username: 'merchant', password: 'merchant123' }}
          onSubmit={handleLogin}
        >
          <FormItem
            label={t('login.username')}
            field="username"
            rules={[{ required: true, message: t('login.usernameRequired') }]}
          >
            <Input placeholder={t('login.usernamePlaceholder')} />
          </FormItem>
          <FormItem
            label={t('login.password')}
            field="password"
            rules={[{ required: true, message: t('login.passwordRequired') }]}
          >
            <Input.Password placeholder={t('login.passwordPlaceholder')} />
          </FormItem>
          <Button type="primary" htmlType="submit" long loading={loading}>
            {t('login.submit')}
          </Button>
        </Form>
      </Card>
    </div>
  )
}

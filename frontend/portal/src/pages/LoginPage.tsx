import { useState } from 'react'
import { Button, Card, Form, Input, Message } from '@arco-design/web-react'
import { useNavigate } from 'react-router-dom'

import { login } from '@/api/auth'
import { userStore } from '@/stores/user'

const FormItem = Form.Item

export default function LoginPage() {
  const navigate = useNavigate()
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
      Message.success('登录成功')
      navigate('/')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <Card className="login-card" title="商家门户登录">
        <Form
          form={form}
          layout="vertical"
          initialValues={{ username: 'merchant', password: 'merchant123' }}
          onSubmit={handleLogin}
        >
          <FormItem
            label="账号"
            field="username"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input placeholder="请输入账号" />
          </FormItem>
          <FormItem
            label="密码"
            field="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password placeholder="请输入密码" />
          </FormItem>
          <Button type="primary" htmlType="submit" long loading={loading}>
            登 录
          </Button>
        </Form>
      </Card>
    </div>
  )
}

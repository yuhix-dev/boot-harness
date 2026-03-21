# User API

## Get current user

`GET /api/v1/users/me`

Returns the authenticated user's profile.

### Response

```json
{
  "id": "c2d5c3b3-3d0d-4f4f-9b8a-6a5d6b4a2b1c",
  "email": "user@example.com",
  "name": "Example User",
  "role": "USER",
  "createdAt": "2024-03-01T12:34:56Z",
  "updatedAt": "2024-03-10T09:20:00Z"
}
```

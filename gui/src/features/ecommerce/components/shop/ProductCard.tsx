import { Box, Card, CardActionArea, CardContent, Chip, Stack, Typography } from '@mui/material';
import ImageNotSupportedIcon from '@mui/icons-material/ImageNotSupported';
import { useNavigate } from 'react-router-dom';
import { ProductSearchResult } from '../../types';

interface Props {
  product: ProductSearchResult;
}

function formatPrice(min: number, max: number): string {
  return min === max ? `$${min.toFixed(2)}` : `$${min.toFixed(2)} – $${max.toFixed(2)}`;
}

export default function ProductCard({ product }: Props): JSX.Element {
  const navigate = useNavigate();

  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardActionArea
        onClick={() => navigate(`/shop/${product.slug}`)}
        sx={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'stretch' }}
      >
        <Box
          sx={{
            height: 180,
            bgcolor: 'action.hover',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            overflow: 'hidden',
          }}
        >
          {product.primaryImageUrl ? (
            <Box
              component="img"
              src={product.primaryImageUrl}
              alt={product.name}
              sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          ) : (
            <ImageNotSupportedIcon sx={{ fontSize: 40, color: 'text.disabled' }} />
          )}
        </Box>
        <CardContent sx={{ flex: 1, width: '100%' }}>
          <Stack spacing={0.75}>
            <Typography variant="body2" fontWeight={600} noWrap title={product.name}>
              {product.name}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {product.categoryName}
            </Typography>
            <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mt: 0.5 }}>
              <Typography variant="body2" fontWeight={700}>
                {formatPrice(product.minPrice, product.maxPrice)}
              </Typography>
              {!product.inStock && (
                <Chip label="Out of stock" size="small" color="default" variant="outlined" />
              )}
            </Stack>
          </Stack>
        </CardContent>
      </CardActionArea>
    </Card>
  );
}

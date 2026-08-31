import { Card, CardActionArea, CardContent, Chip, Stack, Typography } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { ProductSearchResult } from '../../types';
import Thumbnail from '../Thumbnail';

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
        <Thumbnail
          imageUrl={product.primaryImageUrl}
          alt={product.name}
          width="100%"
          height={180}
          borderRadius={0}
          fallbackIconSize={40}
        />
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
